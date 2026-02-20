package com.evenai.companion.ble

import java.util.UUID

/**
 * All BLE UUIDs and protocol constants for the Even Realities G1 glasses.
 *
 * Source: even-realities/EvenDemoApp
 *  - Nordic UART Service: 6E400001-B5A3-F393-E0A9-E50E24DCCA9E
 *  - TX characteristic (app writes to glasses): 6E400002-…
 *  - RX characteristic (app reads from glasses): 6E400003-…
 */
object G1Protocol {

    // ── BLE UUIDs ─────────────────────────────────────────────────────────────
    val SERVICE_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
    val CHAR_TX_UUID: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
    val CHAR_RX_UUID: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
    val CCCD_UUID: UUID    = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // ── Device name suffixes ───────────────────────────────────────────────────
    // Source: EvenDemoApp name-based arm identification
    const val LEFT_SUFFIX  = "_L_"
    const val RIGHT_SUFFIX = "_R_"

    // ── MTU / payload ─────────────────────────────────────────────────────────
    // Source: EvenDemoApp — MTU 251, data payload ≤ 191 bytes per packet
    const val MTU_SIZE   = 251
    const val MAX_PAYLOAD = 191

    // ── Handshake ─────────────────────────────────────────────────────────────
    // Source: EvenDemoApp — [0xF4, 0x01] on service discovery
    val HANDSHAKE: ByteArray = byteArrayOf(0xF4.toByte(), 0x01.toByte())

    // ── ACK values ────────────────────────────────────────────────────────────
    // Source: EvenDemoApp — rsp_status 0xC9 = success, 0xCA = failure
    const val ACK_SUCCESS: Byte = 0xC9.toByte()
    const val ACK_FAILURE: Byte = 0xCA.toByte()

    // ── Command bytes ─────────────────────────────────────────────────────────
    const val CMD_AI_RESULT:  Byte = 0x4E.toByte()  // Send AI text result
    const val CMD_MIC:        Byte = 0x0E.toByte()  // Open/close microphone
    const val CMD_MIC_DATA:   Byte = 0xF1.toByte()  // Inbound mic audio data
    const val CMD_HEARTBEAT:  Byte = 0x25.toByte()  // Keep-alive heartbeat
    const val CMD_TAP_SINGLE: Byte = 0xF5.toByte()  // Touch bar single tap
    const val CMD_TAP_DOUBLE: Byte = 0xF5.toByte()  // Touch bar double tap (same cmd, diff data)
    const val CMD_BMP_DATA:   Byte = 0x15.toByte()  // BMP image data packet
    const val CMD_BMP_END:    Byte = 0x20.toByte()  // End of BMP transmission

    // ── Touch bar sub-bytes ───────────────────────────────────────────────────
    const val TAP_SINGLE_DATA: Byte = 0x01.toByte()
    const val TAP_DOUBLE_DATA: Byte = 0x00.toByte()
    const val TAP_TRIPLE_DATA: Byte = 0x04.toByte()

    // ── newscreen upper-nibble display modes ──────────────────────────────────
    // Source: EvenDemoApp — upper 4 bits of newscreen byte in 0x4E packet
    const val DISPLAY_SHOWING:  Byte = 0x30.toByte()
    const val DISPLAY_COMPLETE: Byte = 0x40.toByte()
    const val DISPLAY_MANUAL:   Byte = 0x50.toByte()
    const val DISPLAY_ERROR:    Byte = 0x60.toByte()

    // ── Display geometry ──────────────────────────────────────────────────────
    // Source: EvenDemoApp — 488 px width, 21 pt font, 5 lines per page,
    //         split 3 lines + 2 lines across two packets
    const val PAGE_WIDTH_PX = 488
    const val FONT_SIZE_PT  = 21
    const val LINES_PER_PAGE = 5
    const val LINES_PACKET_A = 3
    const val LINES_PACKET_B = 2

    // ── Timing ────────────────────────────────────────────────────────────────
    // Source: EvenDemoApp — heartbeat every 8 s, max recording 30 s
    const val HEARTBEAT_INTERVAL_MS = 8_000L
    const val MAX_RECORDING_SEC     = 30
    const val MAX_RETRIES           = 10

    // Source: Reddit review — HUD cooldown ~10-20 s after double tap
    // We use a conservative 2 s debounce before sending new content
    const val WIDGET_SWITCH_DEBOUNCE_MS = 2_000L

    // ── Heartbeat packet builder ───────────────────────────────────────────────
    fun buildHeartbeat(seq: Int): ByteArray {
        val s = (seq and 0xFF).toByte()
        return byteArrayOf(CMD_HEARTBEAT, 0x00, 0x00, s, 0x04, s)
    }

    // ── AI result packet builder ───────────────────────────────────────────────
    // Mirrors the 0x4E packet layout from EvenDemoApp:
    // [0x4E, seq, total_pkg, current_pkg, newscreen, pos_h, pos_l, curr_page, max_page, ...data]
    fun buildTextPacket(
        seq: Int,
        totalPkg: Int,
        currentPkg: Int,
        newscreen: Byte,
        charOffset: Int,
        currPage: Int,
        maxPage: Int,
        data: ByteArray
    ): ByteArray {
        val s    = (seq        and 0xFF).toByte()
        val tot  = (totalPkg   and 0xFF).toByte()
        val cur  = (currentPkg and 0xFF).toByte()
        val posH = ((charOffset shr 8) and 0xFF).toByte()
        val posL = (charOffset and 0xFF).toByte()
        val cp   = (currPage   and 0xFF).toByte()
        val mp   = (maxPage    and 0xFF).toByte()

        return byteArrayOf(CMD_AI_RESULT, s, tot, cur, newscreen, posH, posL, cp, mp) + data
    }

    // ── Mic enable packet builder ─────────────────────────────────────────────
    fun buildMicEnable(enable: Boolean): ByteArray =
        byteArrayOf(CMD_MIC, if (enable) 0x01 else 0x00)
}
