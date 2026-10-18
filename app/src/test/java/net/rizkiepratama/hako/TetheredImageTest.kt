package net.rizkiepratama.hako

import net.rizkiepratama.hako.engine.tether.PtpConstants
import net.rizkiepratama.hako.engine.tether.TetheredImage
import org.junit.Assert.*
import org.junit.Test

class TetheredImageTest {

    @Test
    fun testFormatLabelsAndIsRaw() {
        val jpegImg = TetheredImage(
            id = "img_1",
            objectHandle = 1,
            filename = "DSC_0001.JPG",
            format = PtpConstants.FMT_EXIF_JPEG,
            fileSizeBytes = 5_242_880L
        )

        assertFalse(jpegImg.isRaw)
        assertEquals("JPEG", jpegImg.formatLabel)
        assertEquals("5.00 MB", jpegImg.formattedSize)

        val rawImg = TetheredImage(
            id = "img_2",
            objectHandle = 2,
            filename = "DSC_0002.ARW",
            format = PtpConstants.FMT_ARW,
            fileSizeBytes = 41_943_040L
        )

        assertTrue(rawImg.isRaw)
        assertEquals("ARW / RAW", rawImg.formatLabel)
        assertEquals("40.00 MB", rawImg.formattedSize)
    }

    @Test
    fun testImageEqualityById() {
        val img1 = TetheredImage("id1", 1, "A.JPG", PtpConstants.FMT_EXIF_JPEG, 100)
        val img2 = TetheredImage("id1", 2, "B.JPG", PtpConstants.FMT_EXIF_JPEG, 200)

        assertEquals(img1, img2)
    }
}
