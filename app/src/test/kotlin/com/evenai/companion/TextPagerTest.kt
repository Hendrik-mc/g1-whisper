package com.evenai.companion

import com.evenai.companion.ble.G1Protocol
import com.evenai.companion.ble.TextPager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextPagerTest {

    @Test
    fun `paginate empty string returns empty list`() {
        val pages = TextPager.paginate("")
        assertTrue(pages.isEmpty())
    }

    @Test
    fun `page has at most LINES_PER_PAGE lines`() {
        val longText = (1..20).joinToString(" ") { "word$it" }
        val pages = TextPager.paginate(longText)
        pages.forEach { page ->
            assertTrue(
                "Page has ${page.lines.size} lines, max is ${G1Protocol.LINES_PER_PAGE}",
                page.lines.size <= G1Protocol.LINES_PER_PAGE
            )
        }
    }

    @Test
    fun `packet A contains first 3 lines, packet B contains last 2`() {
        val page = TextPager.Page(listOf("a", "b", "c", "d", "e"))
        assertEquals(listOf("a", "b", "c"), page.packetALines)
        assertEquals(listOf("d", "e"), page.packetBLines)
    }

    @Test
    fun `buildPackets returns 2 packets`() {
        val page = TextPager.Page(listOf("Hello world", "Line two", "Line three", "Line four", "Line five"))
        val packets = TextPager.buildPackets(page, 0, 1, 0, true)
        assertEquals(2, packets.size)
    }

    @Test
    fun `each packet starts with CMD_AI_RESULT`() {
        val page = TextPager.Page(listOf("Test"))
        val packets = TextPager.buildPackets(page, 0, 1, 0, true)
        packets.forEach { pkt ->
            assertEquals(G1Protocol.CMD_AI_RESULT, pkt[0])
        }
    }

    @Test
    fun `payload does not exceed MAX_PAYLOAD bytes`() {
        val longLine = "A".repeat(500)
        val page = TextPager.Page(listOf(longLine))
        val packets = TextPager.buildPackets(page, 0, 1, 0, true)
        packets.forEach { pkt ->
            val payloadSize = pkt.size - 9 // 9-byte header
            assertTrue(
                "Payload $payloadSize exceeds MAX_PAYLOAD ${G1Protocol.MAX_PAYLOAD}",
                payloadSize <= G1Protocol.MAX_PAYLOAD
            )
        }
    }

    @Test
    fun `buildHeartbeat has correct structure`() {
        val hb = G1Protocol.buildHeartbeat(42)
        assertEquals(G1Protocol.CMD_HEARTBEAT, hb[0])
        assertEquals(6, hb.size)
        assertEquals(42.toByte(), hb[3])
        assertEquals(0x04.toByte(), hb[4])
        assertEquals(42.toByte(), hb[5])
    }

    @Test
    fun `buildMicEnable enable packet has correct bytes`() {
        val on  = G1Protocol.buildMicEnable(true)
        val off = G1Protocol.buildMicEnable(false)
        assertEquals(G1Protocol.CMD_MIC, on[0])
        assertEquals(0x01.toByte(), on[1])
        assertEquals(G1Protocol.CMD_MIC, off[0])
        assertEquals(0x00.toByte(), off[1])
    }
}
