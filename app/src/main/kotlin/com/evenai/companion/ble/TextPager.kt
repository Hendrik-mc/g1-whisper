package com.evenai.companion.ble

import android.graphics.Paint
import android.util.Log

private const val TAG = "TextPager"

/**
 * Paginates text for the G1 lens display.
 *
 * Source grounding (EvenDemoApp):
 *  - 488 px display width
 *  - 21 pt font size
 *  - 5 lines per screen page
 *  - Split: first 3 lines → packet A, last 2 lines → packet B
 *  - Auto-pagination at 8-second intervals (handled by caller)
 */
object TextPager {

    private const val PAGE_WIDTH_PX = G1Protocol.PAGE_WIDTH_PX   // 488
    private const val FONT_SIZE_PT  = G1Protocol.FONT_SIZE_PT     // 21
    private const val LINES_PER_PAGE = G1Protocol.LINES_PER_PAGE  // 5

    /**
     * Represents a single paginated page containing up to 5 display lines.
     * Lines split into packet A (lines 0-2) and packet B (lines 3-4).
     */
    data class Page(val lines: List<String>) {
        val packetALines: List<String> get() = lines.take(G1Protocol.LINES_PACKET_A)
        val packetBLines: List<String> get() = lines.drop(G1Protocol.LINES_PACKET_A)
    }

    /**
     * Converts raw text into a list of display pages.
     * Uses a Paint object to measure text width at 21 pt.
     */
    fun paginate(text: String): List<Page> {
        val wrappedLines = wrapText(text)
        Log.d(TAG, "Wrapped ${wrappedLines.size} lines from ${text.length} chars")

        return wrappedLines.chunked(LINES_PER_PAGE).map { Page(it) }
    }

    /**
     * Wraps text into lines that fit within 488 px at 21 pt.
     * Uses Android's Paint for accurate pixel measurement.
     */
    fun wrapText(text: String): List<String> {
        val paint = Paint().apply {
            textSize = ptToPx(FONT_SIZE_PT.toFloat())
        }

        val lines = mutableListOf<String>()
        val paragraphs = text.split("\n")

        for (para in paragraphs) {
            if (para.isBlank()) {
                lines.add("")
                continue
            }
            val words = para.split(" ")
            val currentLine = StringBuilder()

            for (word in words) {
                val candidate = if (currentLine.isEmpty()) word else "${currentLine} $word"
                if (paint.measureText(candidate) <= PAGE_WIDTH_PX) {
                    currentLine.clear()
                    currentLine.append(candidate)
                } else {
                    if (currentLine.isNotEmpty()) lines.add(currentLine.toString())
                    currentLine.clear()
                    currentLine.append(word)
                }
            }
            if (currentLine.isNotEmpty()) lines.add(currentLine.toString())
        }

        return lines
    }

    /**
     * Builds the two BLE byte payloads (packet A + B) for a given page and page index.
     * Returns a list of [ByteArray] ready to send via [DualBleConnection.sendBoth].
     */
    fun buildPackets(
        page: Page,
        pageIndex: Int,
        totalPages: Int,
        seq: Int,
        isLastPage: Boolean
    ): List<ByteArray> {
        val newscreen = if (isLastPage) G1Protocol.DISPLAY_COMPLETE else G1Protocol.DISPLAY_SHOWING

        val packets = mutableListOf<ByteArray>()
        var charOffset = 0

        // Packet A — first 3 lines
        val dataA = page.packetALines.joinToString("\n").toByteArray(Charsets.UTF_8)
            .take(G1Protocol.MAX_PAYLOAD).toByteArray()
        packets.add(
            G1Protocol.buildTextPacket(
                seq         = seq,
                totalPkg    = 2,
                currentPkg  = 0,
                newscreen   = newscreen,
                charOffset  = charOffset,
                currPage    = pageIndex,
                maxPage     = totalPages - 1,
                data        = dataA
            )
        )
        charOffset += dataA.size

        // Packet B — last 2 lines (may be empty on the last page)
        val dataB = page.packetBLines.joinToString("\n").toByteArray(Charsets.UTF_8)
            .take(G1Protocol.MAX_PAYLOAD).toByteArray()
        packets.add(
            G1Protocol.buildTextPacket(
                seq         = (seq + 1) and 0xFF,
                totalPkg    = 2,
                currentPkg  = 1,
                newscreen   = newscreen,
                charOffset  = charOffset,
                currPage    = pageIndex,
                maxPage     = totalPages - 1,
                data        = dataB
            )
        )

        return packets
    }

    // Convert pt to px assuming 160 dpi baseline (1 pt = 1.333 px at 160 dpi)
    private fun ptToPx(pt: Float): Float = pt * (160f / 72f)
}
