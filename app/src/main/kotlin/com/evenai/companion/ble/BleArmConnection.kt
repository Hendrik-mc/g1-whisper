package com.evenai.companion.ble

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "BleArmConnection"
private const val WRITE_TIMEOUT_MS = 2_000L
private const val CONNECT_TIMEOUT_MS = 10_000L

/**
 * Manages one BLE GATT connection to a single G1 arm (left or right).
 *
 * Source grounding:
 *  - Nordic UART service, TX/RX characteristics from EvenDemoApp
 *  - Handshake [0xF4, 0x01] sent after services discovered
 *  - Up to MAX_RETRIES retries per write
 *  - ACK_SUCCESS / ACK_FAILURE from response byte 0xC9 / 0xCA
 */
class BleArmConnection(
    private val context: Context,
    val device: BluetoothDevice,
    val arm: Arm,
    private val onStateChange: (Arm, ArmState) -> Unit,
    private val onDataReceived: (Arm, ByteArray) -> Unit
) {
    enum class Arm { LEFT, RIGHT }

    sealed class ArmState {
        object Connecting : ArmState()
        object Connected  : ArmState()
        object Ready      : ArmState()   // handshake complete
        object Disconnected : ArmState()
        data class Error(val msg: String) : ArmState()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var gatt: BluetoothGatt? = null
    private var txChar: BluetoothGattCharacteristic? = null
    private var rxChar: BluetoothGattCharacteristic? = null
    private val writeAckChannel = Channel<Boolean>(Channel.CONFLATED)
    private var heartbeatJob: Job? = null
    private var heartbeatSeq = 0

    var state: ArmState = ArmState.Disconnected
        private set(value) {
            field = value
            onStateChange(arm, value)
        }

    // ── Connect ───────────────────────────────────────────────────────────────
    fun connect() {
        if (!hasBluetoothPermission()) {
            state = ArmState.Error("Bluetooth permission not granted")
            return
        }
        state = ArmState.Connecting
        Log.d(TAG, "[$arm] connecting to ${device.address}")
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    // ── Disconnect ────────────────────────────────────────────────────────────
    fun disconnect() {
        heartbeatJob?.cancel()
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        txChar = null
        rxChar = null
        state = ArmState.Disconnected
    }

    // ── Write with retry ──────────────────────────────────────────────────────
    // Source: EvenDemoApp — up to MAX_RETRIES retries
    suspend fun writeWithRetry(data: ByteArray): Boolean {
        repeat(G1Protocol.MAX_RETRIES) { attempt ->
            val success = writeSingle(data)
            if (success) return true
            Log.w(TAG, "[$arm] write attempt ${attempt + 1} failed, retrying…")
            delay(100)
        }
        Log.e(TAG, "[$arm] all retries exhausted")
        return false
    }

    // ── GATT callbacks ────────────────────────────────────────────────────────
    @Suppress("DEPRECATION")
    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "[$arm] connected, discovering services")
                state = ArmState.Connected
                g.requestMtu(G1Protocol.MTU_SIZE)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "[$arm] disconnected")
                heartbeatJob?.cancel()
                state = ArmState.Disconnected
                g.close()
                gatt = null
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            Log.d(TAG, "[$arm] MTU set to $mtu")
            g.discoverServices()
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                state = ArmState.Error("Service discovery failed: $status")
                return
            }
            val service = g.getService(G1Protocol.SERVICE_UUID)
            if (service == null) {
                state = ArmState.Error("Nordic UART service not found")
                return
            }
            txChar = service.getCharacteristic(G1Protocol.CHAR_TX_UUID)
            rxChar = service.getCharacteristic(G1Protocol.CHAR_RX_UUID)

            // Enable notifications on RX characteristic
            rxChar?.let { rx ->
                g.setCharacteristicNotification(rx, true)
                val desc = rx.getDescriptor(G1Protocol.CCCD_UUID)
                desc?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                g.writeDescriptor(desc)
            }

            // Send handshake after a short delay to let notifications settle
            scope.launch {
                delay(300)
                writeSingle(G1Protocol.HANDSHAKE)
                state = ArmState.Ready
                startHeartbeat()
            }
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val data = characteristic.value ?: return
            onDataReceived(arm, data)

            // Check for ACK bytes in response
            if (data.isNotEmpty()) {
                val ack = data[0] == G1Protocol.ACK_SUCCESS
                writeAckChannel.trySend(ack)
            }
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            writeAckChannel.trySend(status == BluetoothGatt.GATT_SUCCESS)
        }
    }

    // ── Internal write ────────────────────────────────────────────────────────
    @Suppress("DEPRECATION")
    private suspend fun writeSingle(data: ByteArray): Boolean {
        val g = gatt ?: return false
        val tx = txChar ?: return false
        tx.value = data
        tx.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        val initiated = g.writeCharacteristic(tx)
        if (!initiated) return false
        return withTimeoutOrNull(WRITE_TIMEOUT_MS) { writeAckChannel.receive() } ?: false
    }

    // ── Heartbeat ─────────────────────────────────────────────────────────────
    // Source: EvenDemoApp — 8-second heartbeat interval
    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (true) {
                delay(G1Protocol.HEARTBEAT_INTERVAL_MS)
                writeWithRetry(G1Protocol.buildHeartbeat(heartbeatSeq++))
            }
        }
    }

    private fun hasBluetoothPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
}
