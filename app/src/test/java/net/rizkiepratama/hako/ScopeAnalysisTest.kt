package net.rizkiepratama.hako

import net.rizkiepratama.hako.engine.analyzeRgbaFrame
import net.rizkiepratama.hako.engine.SCOPE_LEVELS
import net.rizkiepratama.hako.engine.renderDensityTrace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScopeAnalysisTest {
    @Test
    fun neutralGrayPlacesChromaAtTheCenterOfBothParadeLanes() {
        val frame = analyzeRgbaFrame(byteArrayOf(128.toByte(), 128.toByte(), 128.toByte(), -1), 1, 1)
        val laneSize = frame.yuvParadeDensity.size / 3
        assertEquals(1, frame.yuvParadeDensity[laneSize + SCOPE_LEVELS / 2])
        assertEquals(1, frame.yuvParadeDensity[2 * laneSize + SCOPE_LEVELS / 2])
    }

    @Test
    fun waveformRetainsShadowsAndHighlightsInTheSameColumn() {
        val frame = analyzeRgbaFrame(byteArrayOf(0, 0, 0, -1, -1, -1, -1, -1), 1, 2)
        assertEquals(2, frame.lumaDensity.sum())
        assertEquals(1, frame.lumaDensity[0])
        assertEquals(1, frame.lumaDensity[SCOPE_LEVELS - 1])
    }

    @Test
    fun coincidentRgbTracesAddToNeutralWhiteWithoutLightingEmptyAreas() {
        val density = IntArray(3 * 8 * SCOPE_LEVELS)
        repeat(3) { density[it * 8 * SCOPE_LEVELS + 4 * SCOPE_LEVELS + 128] = 10 }
        val raster = renderDensityTrace(density, 8, 3, false, listOf(0xff0000, 0x00ff00, 0x0000ff), 10)
        val pixel = raster.pixels[(SCOPE_LEVELS - 1 - 128) * raster.width + 4]
        assertTrue((pixel and 255) > 0)
        assertEquals(pixel and 255, (pixel shr 8) and 255)
        assertEquals(pixel and 255, (pixel shr 16) and 255)
        assertEquals(0xff000000.toInt(), raster.pixels[0])
    }

    @Test
    fun splitTracesStayInTheirOwnLanes() {
        val density = IntArray(3 * 8 * SCOPE_LEVELS)
        repeat(3) { density[it * 8 * SCOPE_LEVELS + 4 * SCOPE_LEVELS + 128] = 10 }
        val raster = renderDensityTrace(density, 8, 3, true, listOf(0xff0000, 0x00ff00, 0x0000ff), 10)
        assertEquals(24, raster.width)
        val row = (SCOPE_LEVELS - 1 - 128) * raster.width
        assertEquals(0, raster.pixels[row + 4] and 0x00ffff)
        assertEquals(0, raster.pixels[row + 12] and 0xff00ff)
        assertEquals(0, raster.pixels[row + 20] and 0xffff00)
    }

    @Test
    fun colorHistogramsShareOnePopulationScale() {
        val frame = analyzeRgbaFrame(byteArrayOf(0, 0, 0, -1, 127, 0, 0, -1, -1, 0, 0, -1), 3, 1)
        assertEquals(1f, frame.greenHistogramNormalized[0], .001f)
        assertEquals(1f / 3, frame.redHistogramNormalized[127], .001f)
    }

    @Test
    fun rgbaPixelsPopulateChannelAndLumaHistograms() {
        val frame = analyzeRgbaFrame(
            rgba = byteArrayOf(
                255.toByte(), 0, 0, 255.toByte(),
                0, 255.toByte(), 0, 255.toByte(),
                0, 0, 255.toByte(), 255.toByte(), 255.toByte()
            ),
            width = 3,
            height = 1
        )

        assertEquals(1, frame.redHistogram[255])
        assertEquals(1, frame.greenHistogram[255])
        assertEquals(1, frame.blueHistogram[255])
        assertEquals(3, frame.lumaHistogram.sum())
        assertTrue(frame.vectorDensity.sum() > 0)
    }

    @Test
    fun analysisIsNormalisedAgainstTheBrightestBin() {
        val frame = analyzeRgbaFrame(
            rgba = ByteArray(4 * 8) { index -> if (index % 4 == 0) 255.toByte() else 0 },
            width = 8,
            height = 1
        )

        assertEquals(1f, frame.redHistogramNormalized[255], 0.001f)
        assertEquals(0f, frame.redHistogramNormalized[0], 0.001f)
    }

    @Test
    fun paradeAnalysisKeepsTheFullPerColumnSpectrum() {
        val frame = analyzeRgbaFrame(
            rgba = byteArrayOf(
                255.toByte(), 0, 0, 255.toByte(),
                255.toByte(), 128.toByte(), 0, 255.toByte(),
                255.toByte(), 255.toByte(), 0, 255.toByte()
            ),
            width = 3,
            height = 1
        )

        assertEquals(9, frame.rgbParadeDensity.sum())
        assertTrue(frame.rgbParadeDensity.count { it > 0 } > 1)
    }
}
