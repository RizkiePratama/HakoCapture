package net.rizkiepratama.hako

import net.rizkiepratama.hako.engine.tether.PtpConstants
import net.rizkiepratama.hako.engine.tether.PtpPacket
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PtpPacketTest {

    @Test
    fun standardObjectOpcodesMatchPtpSpecification() {
        assertEquals(0x1007, PtpConstants.OC_GET_OBJECT_HANDLES)
        assertEquals(0x1008, PtpConstants.OC_GET_OBJECT_INFO)
        assertEquals(0x1009, PtpConstants.OC_GET_OBJECT)
    }

    @Test
    fun testCommandPacketLengthAndEncoding() {
        val pkt = PtpPacket.createCommand(PtpConstants.OC_OPEN_SESSION, 1, 100)
        assertEquals(PtpConstants.HEADER_SIZE + 4, pkt.length)

        val encoded = pkt.encode()
        assertEquals(pkt.length, encoded.size)

        val buf = ByteBuffer.wrap(encoded).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(16, buf.int) // Length = 12 + 4
        assertEquals(PtpConstants.TYPE_COMMAND, buf.short.toInt() and 0xFFFF)
        assertEquals(PtpConstants.OC_OPEN_SESSION, buf.short.toInt() and 0xFFFF)
        assertEquals(1, buf.int) // Transaction ID
        assertEquals(100, buf.int) // Param 1
    }

    @Test
    fun testPacketDecoding() {
        val original = PtpPacket.createCommand(PtpConstants.OC_GET_OBJECT_INFO, 42, 0x12345678)
        val bytes = original.encode()

        val decoded = PtpPacket.decode(bytes)
        assertNotNull(decoded)
        assertEquals(PtpConstants.TYPE_COMMAND, decoded!!.type)
        assertEquals(PtpConstants.OC_GET_OBJECT_INFO, decoded.code)
        assertEquals(42, decoded.transactionId)
    }

    @Test
    fun testInvalidDecodeReturnsNull() {
        val shortBytes = byteArrayOf(1, 2, 3)
        assertNull(PtpPacket.decode(shortBytes))
    }
}
