package com.evenai.companion.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "GlassesScanner"
private const val SCAN_TIMEOUT_MS = 15_000L

/**
 * BLE scanner that discovers G1 glasses by the Nordic UART service UUID.
 *
 * Source grounding (EvenDemoApp):
 *  - Device names containing "_L_" identify the left arm
 *  - Device names containing "_R_" identify the right arm
 *  - Both must be found before connecting
 */
class GlassesScanner(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter
) {
    data class ScanState(
        val isScanning: Boolean = false,
        val leftDevice:  BluetoothDevice? = null,
        val rightDevice: BluetoothDevice? = null,
        val error: String? = null
    ) {
        val bothFound: Boolean get() = leftDevice != null && rightDevice != null
    }

    private val _scanState = MutableStateFlow(ScanState())
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val scanner = bluetoothAdapter.bluetoothLeScanner

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name   = result.device.name ?: result.scanRecord?.deviceName ?: return

            when {
                isLeftDevice(name) -> {
                    Log.d(TAG, "Found left arm: $name ${device.address}")
                    _scanState.value = _scanState.value.copy(leftDevice = device)
                }
                isRightDevice(name) -> {
                    Log.d(TAG, "Found right arm: $name ${device.address}")
                    _scanState.value = _scanState.value.copy(rightDevice = device)
                }
            }

            if (_scanState.value.bothFound) {
                Log.d(TAG, "Both arms found — stopping scan")
                stopScan()
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed: $errorCode")
            _scanState.value = _scanState.value.copy(
                isScanning = false,
                error = "Scan failed (code $errorCode)"
            )
        }
    }

    fun startScan() {
        if (!hasPermission()) {
            _scanState.value = _scanState.value.copy(error = "Missing Bluetooth permission")
            return
        }
        if (_scanState.value.isScanning) return

        _scanState.value = ScanState(isScanning = true)

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(G1Protocol.SERVICE_UUID))
                .build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner?.startScan(filters, settings, scanCallback)
        Log.d(TAG, "BLE scan started")
    }

    fun stopScan() {
        if (!hasPermission()) return
        scanner?.stopScan(scanCallback)
        _scanState.value = _scanState.value.copy(isScanning = false)
        Log.d(TAG, "BLE scan stopped")
    }

    fun reset() {
        stopScan()
        _scanState.value = ScanState()
    }

    // ── Arm identification ─────────────────────────────────────────────────────
    // Source: EvenDemoApp — name-based identification
    fun isLeftDevice(name: String):  Boolean = name.contains(G1Protocol.LEFT_SUFFIX,  ignoreCase = true)
    fun isRightDevice(name: String): Boolean = name.contains(G1Protocol.RIGHT_SUFFIX, ignoreCase = true)

    private fun hasPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED
}
