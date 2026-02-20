package com.evenai.companion.ble

import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "DualBleConnection"

/**
 * Abstracts the two independent BLE connections (left arm + right arm) as one unit.
 *
 * Source grounding (EvenDemoApp):
 *  - Each arm has its own BluetoothGatt instance
 *  - Device names containing "_L_" = left, "_R_" = right
 *  - Send order: left FIRST, wait for response, then right
 *  - isBothConnected() validates both arms are Ready
 */
class DualBleConnection(private val context: Context) {

    data class ConnectionState(
        val leftState:  BleArmConnection.ArmState = BleArmConnection.ArmState.Disconnected,
        val rightState: BleArmConnection.ArmState = BleArmConnection.ArmState.Disconnected
    ) {
        val bothReady: Boolean
            get() = leftState is BleArmConnection.ArmState.Ready &&
                    rightState is BleArmConnection.ArmState.Ready

        val leftReady: Boolean  get() = leftState  is BleArmConnection.ArmState.Ready
        val rightReady: Boolean get() = rightState is BleArmConnection.ArmState.Ready

        val anyConnected: Boolean
            get() = leftState  !is BleArmConnection.ArmState.Disconnected ||
                    rightState !is BleArmConnection.ArmState.Disconnected
    }

    private val _connectionState = MutableStateFlow(ConnectionState())
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // Inbound data listeners
    private val _incomingData = MutableStateFlow<Pair<BleArmConnection.Arm, ByteArray>?>(null)

    private var leftArm:  BleArmConnection? = null
    private var rightArm: BleArmConnection? = null

    // ── Connect both arms ─────────────────────────────────────────────────────
    fun connect(
        leftDevice:  android.bluetooth.BluetoothDevice,
        rightDevice: android.bluetooth.BluetoothDevice
    ) {
        leftArm = BleArmConnection(
            context = context,
            device  = leftDevice,
            arm     = BleArmConnection.Arm.LEFT,
            onStateChange = { arm, state ->
                Log.d(TAG, "Left arm state: $state")
                _connectionState.value = _connectionState.value.copy(leftState = state)
            },
            onDataReceived = { arm, data ->
                _incomingData.value = Pair(arm, data)
            }
        ).also { it.connect() }

        rightArm = BleArmConnection(
            context = context,
            device  = rightDevice,
            arm     = BleArmConnection.Arm.RIGHT,
            onStateChange = { arm, state ->
                Log.d(TAG, "Right arm state: $state")
                _connectionState.value = _connectionState.value.copy(rightState = state)
            },
            onDataReceived = { arm, data ->
                _incomingData.value = Pair(arm, data)
            }
        ).also { it.connect() }
    }

    // ── Disconnect both arms ──────────────────────────────────────────────────
    fun disconnectAll() {
        leftArm?.disconnect()
        rightArm?.disconnect()
        leftArm  = null
        rightArm = null
        _connectionState.value = ConnectionState()
    }

    // ── Send to both arms — LEFT FIRST, then RIGHT ────────────────────────────
    // Source: EvenDemoApp — strict left → right send order
    // Returns true only if both writes succeed
    suspend fun sendBoth(data: ByteArray): Boolean {
        val left  = leftArm  ?: return false
        val right = rightArm ?: return false

        val leftOk = left.writeWithRetry(data)
        if (!leftOk) {
            Log.w(TAG, "Left arm write failed; aborting right arm send")
            return false
        }

        // Small delay between arms as observed in EvenDemoApp
        delay(20)

        val rightOk = right.writeWithRetry(data)
        return rightOk
    }

    // ── Send to one arm only ──────────────────────────────────────────────────
    // Source: EvenDemoApp — microphone command targets right arm only
    suspend fun sendToLeft(data: ByteArray): Boolean =
        leftArm?.writeWithRetry(data) ?: false

    suspend fun sendToRight(data: ByteArray): Boolean =
        rightArm?.writeWithRetry(data) ?: false

    // ── Resync — re-send current page to both arms ────────────────────────────
    // Source: Reddit review — "setup screens out of sync; restarting setup resolved it"
    // Instead of restarting, we re-send the same payload to re-align displays
    suspend fun resync(data: ByteArray): Boolean {
        Log.d(TAG, "Resyncing both arms…")
        return sendBoth(data)
    }

    val incomingData = _incomingData.asStateFlow()
}
