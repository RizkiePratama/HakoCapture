package net.rizkiepratama.hako

import net.rizkiepratama.hako.ui.screens.previewSizeForContainer
import net.rizkiepratama.hako.ui.screens.PreviewSize
import net.rizkiepratama.hako.engine.CaptureMode
import net.rizkiepratama.hako.engine.collapseCaptureModes
import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewLayoutTest {
    @Test
    fun duplicateFormatsCollapseToOneAutoFpsMode() {
        val modes = collapseCaptureModes(
            listOf(
                CaptureMode(1920, 1080, null, 1),
                CaptureMode(1920, 1080, null, 0)
            )
        )

        assertEquals(listOf(CaptureMode(1920, 1080, null, 1)), modes)
    }

    @Test
    fun captureModeCombinesResolutionAndRefreshRateInOneLabel() {
        assertEquals(
            "1920×1080 • 60 fps",
            CaptureMode(1920, 1080, 60, 1).label
        )
    }

    @Test
    fun captureModeExplainsWhenRefreshRateIsNotExposed() {
        assertEquals(
            "1280×720 • Auto fps",
            CaptureMode(1280, 720, null, 1).label
        )
    }

    @Test
    fun portraitFitsCaptureToDisplayWidth() {
        assertEquals(
            PreviewSize(1080, 608),
            previewSizeForContainer(
                containerWidth = 1080,
                containerHeight = 2400,
                sourceWidth = 1920,
                sourceHeight = 1080
            )
        )
    }

    @Test
    fun landscapeFitsCaptureToDisplayHeight() {
        assertEquals(
            PreviewSize(1920, 1080),
            previewSizeForContainer(
                containerWidth = 2400,
                containerHeight = 1080,
                sourceWidth = 1920,
                sourceHeight = 1080
            )
        )
    }

    @Test
    fun preservesFourByThreeFallbackAspectRatio() {
        assertEquals(
            PreviewSize(1080, 810),
            previewSizeForContainer(
                containerWidth = 1080,
                containerHeight = 2400,
                sourceWidth = 640,
                sourceHeight = 480
            )
        )
    }
}
