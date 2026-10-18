package net.rizkiepratama.hako

import net.rizkiepratama.hako.engine.tether.RawImageDecoder
import org.junit.Assert.*
import org.junit.Test

class RawImageDecoderTest {

    @Test
    fun testExtractsEmbeddedJpegFromRawContainer() {
        // Construct dummy RAW container bytes embedding a valid SOI-EOI JPEG block
        val prefix = byteArrayOf(0x49, 0x49, 0x2A, 0x00, 0x08, 0x00) // TIFF header dummy
        val jpegBody = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), // SOI
            0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, // JFIF header
            0x01, 0x02, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
            0xFF.toByte(), 0xD9.toByte()  // EOI
        )
        val suffix = byteArrayOf(0x00, 0x00, 0x00, 0x00)

        val rawContainer = prefix + jpegBody + suffix

        val extracted = RawImageDecoder.extractLargestJpeg(rawContainer)
        assertNotNull(extracted)
        assertArrayEquals(jpegBody, extracted)
    }

    @Test
    fun testSelectsLargestJpegWhenMultiplePreviewsPresent() {
        val smallJpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x01, 0x02, 0xFF.toByte(), 0xD9.toByte())
        val largeJpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0xFF.toByte(), 0xD9.toByte())

        val rawContainer = byteArrayOf(0x00, 0x00) + smallJpeg + byteArrayOf(0x11, 0x22) + largeJpeg + byteArrayOf(0x33)

        val extracted = RawImageDecoder.extractLargestJpeg(rawContainer)
        assertNotNull(extracted)
        assertArrayEquals(largeJpeg, extracted)
    }

    @Test
    fun testReturnsNullIfNoJpegFound() {
        val randomBytes = byteArrayOf(0x10, 0x20, 0x30, 0x40, 0x50)
        assertNull(RawImageDecoder.extractLargestJpeg(randomBytes))
    }
}
