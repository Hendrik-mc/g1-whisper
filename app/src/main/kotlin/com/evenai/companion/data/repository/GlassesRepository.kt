package com.evenai.companion.data.repository

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.util.Log
import com.evenai.companion.ble.BleArmConnection
import com.evenai.companion.ble.DualBleConnection
import com.evenai.companion.ble.G1Protocol
import com.evenai.companion.ble.GlassesScanner
import com.evenai.companion.ble.TextPager
import com.evenai.companion.domain.model.GlassesState
import com.evenai.companion.domain.model.LensPage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GlassesRepository"

@Singleton
class GlassesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val scanner = GlassesScanner(context, bluetoothAdapter)
    val dual    = DualBleConnection(context)

    private val _glassesState = MutableStateFlow<GlassesState>(GlassesState.Disconnected)
    val glassesState: StateFlow<GlassesState> = _glassesState.asStateFlow()

    private val _currentPage = MutableStateFlow<LensPage?>(null)
    val currentPage: StateFlow<LensPage?> = _currentPage.asStateFlow()

    private var seqCounter = 0
    private var autoPageJob: Job? = null

    // ── Scanning ──────────────────────────────────────────────────────────────
    fun startScan() {
        _glassesState.value = GlassesState.Scanning
        scanner.startScan()

        scope.launch {
            // Wait until both arms found, then connect
            scanner.scanState.collect { state ->
                if (state.bothFound) {
                    val left  = state.leftDevice!!
                    val right = state.rightDevice!!
                    scanner.stopScan()
                    connect(left, right)
                    return@collect
                }
                if (state.error != null) {
                    _glassesState.value = GlassesState.Error(state.error)
                }
            }
        }
    }

    fun stopScan() {
        scanner.stopScan()
        if (_glassesState.value is GlassesState.Scanning) {
            _glassesState.value = GlassesState.Disconnected
        }
    }

    // ── Connect ───────────────────────────────────────────────────────────────
    private fun connect(
        leftDevice: android.bluetooth.BluetoothDevice,
        rightDevice: android.bluetooth.BluetoothDevice
    ) {
        _glassesState.value = GlassesState.Connecting
        dual.connect(leftDevice, rightDevice)

        scope.launch {
            dual.connectionState.collect { cs ->
                _glassesState.value = when {
                    cs.bothReady  -> GlassesState.Ready
                    cs.anyConnected -> GlassesState.Connected
                    else -> GlassesState.Disconnected
                }
            }
        }
    }

    fun disconnect() {
        dual.disconnectAll()
        _glassesState.value = GlassesState.Disconnected
    }

    // ── Reconnect ─────────────────────────────────────────────────────────────
    // Triggered from ResyncScreen or automatically on disconnect
    fun reconnect() {
        disconnect()
        startScan()
    }

    // ── Text display ──────────────────────────────────────────────────────────
    /**
     * Sends text to both lenses, paginated.
     * Source: EvenDemoApp paging + left→right send order.
     * Reddit: adds debounce delay after widget switch for HUD cooldown.
     */
    suspend fun displayText(
        text: String,
        debounce: Boolean = false
    ) {
        if (debounce) {
            delay(G1Protocol.WIDGET_SWITCH_DEBOUNCE_MS)
        }

        val pages = TextPager.paginate(text)
        if (pages.isEmpty()) return

        pages.forEachIndexed { index, page ->
            val packets = TextPager.buildPackets(
                page       = page,
                pageIndex  = index,
                totalPages = pages.size,
                seq        = seqCounter,
                isLastPage = index == pages.size - 1
            )
            seqCounter = (seqCounter + packets.size) and 0xFF

            for (packet in packets) {
                val ok = dual.sendBoth(packet)
                if (!ok) {
                    Log.w(TAG, "Failed to send packet $index, triggering OutOfSync")
                    _glassesState.value = GlassesState.OutOfSync
                    return
                }
                delay(50) // inter-packet gap
            }

            _currentPage.value = LensPage(
                lines      = page.lines,
                pageIndex  = index,
                totalPages = pages.size
            )

            // Auto-advance to next page after 8 seconds (EvenDemoApp pagination interval)
            if (index < pages.size - 1) {
                delay(G1Protocol.HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    // ── Resync ────────────────────────────────────────────────────────────────
    // Source: Reddit — "restarting setup resolved out-of-sync screens"
    // We resend the current page payload to re-align both lenses
    suspend fun resync() {
        val page = _currentPage.value ?: return
        Log.d(TAG, "Resyncing lenses at page ${page.pageIndex}")
        // Re-paginate from current page content and resend
        displayText(page.lines.joinToString("\n"))
        if (_glassesState.value is GlassesState.OutOfSync) {
            _glassesState.value = GlassesState.Ready
        }
    }

    // ── Microphone ────────────────────────────────────────────────────────────
    // Source: EvenDemoApp — mic command 0x0E targets right arm only
    suspend fun setMicEnabled(enabled: Boolean): Boolean {
        val packet = G1Protocol.buildMicEnable(enabled)
        return dual.sendToRight(packet)
    }
}
