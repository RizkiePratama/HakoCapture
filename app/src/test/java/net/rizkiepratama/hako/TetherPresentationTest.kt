package net.rizkiepratama.hako

import net.rizkiepratama.hako.engine.tether.tetherFilename
import net.rizkiepratama.hako.engine.tether.storedTetherFormat
import net.rizkiepratama.hako.engine.tether.storedTetherTimestampMs
import net.rizkiepratama.hako.engine.tether.tetherThumbnailFilename
import net.rizkiepratama.hako.engine.tether.PtpConstants
import net.rizkiepratama.hako.ui.screens.useLandscapeTetherLayout
import net.rizkiepratama.hako.ui.screens.useWideLandingLayout
import net.rizkiepratama.hako.ui.screens.tetherActionImageIds
import net.rizkiepratama.hako.ui.screens.tetherZoomAfterDoubleTap
import net.rizkiepratama.hako.ui.screens.clampTetherZoom
import net.rizkiepratama.hako.ui.screens.clampTetherPan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TetherPresentationTest {
    @Test
    fun rawCaptureGetsUniqueArwFilename() {
        assertEquals("HAKO_42_7.ARW", tetherFilename(42, 7, PtpConstants.FMT_SONY_RAW))
    }

    @Test
    fun layoutFollowsAvailableAspectRatio() {
        assertTrue(useLandscapeTetherLayout(1200, 700))
        assertFalse(useLandscapeTetherLayout(700, 1200))
    }

    @Test
    fun storedTetherMetadataRestoresRawAndJpegCaptures() {
        assertEquals(PtpConstants.FMT_ARW, storedTetherFormat("image/x-sony-arw", "HAKO_1.ARW"))
        assertEquals(PtpConstants.FMT_EXIF_JPEG, storedTetherFormat("image/jpeg", "HAKO_2.JPG"))
        assertEquals(1_725_000L, storedTetherTimestampMs(1_725L))
        assertEquals("HAKO_1.ARW.preview.jpg", tetherThumbnailFilename("HAKO_1.ARW"))
    }

    @Test
    fun landingStacksCardsOnPortraitAndUsesColumnsOnWideScreens() {
        assertFalse(useWideLandingLayout(480, 900))
        assertTrue(useWideLandingLayout(1000, 700))
    }

    @Test
    fun tetherActionsTargetFocusedImageOrMultiSelection() {
        assertEquals(listOf("focused"), tetherActionImageIds(false, "focused", setOf("other")))
        assertEquals(listOf("a", "b"), tetherActionImageIds(true, "focused", linkedSetOf("a", "b")))
        assertEquals(emptyList<String>(), tetherActionImageIds(true, "focused", emptySet()))
    }

    @Test
    fun tetherPreviewDoubleTapTogglesZoomAndPinchStaysBounded() {
        assertEquals(2f, tetherZoomAfterDoubleTap(1f))
        assertEquals(1f, tetherZoomAfterDoubleTap(2f))
        assertEquals(1f, clampTetherZoom(0.25f))
        assertEquals(5f, clampTetherZoom(8f))
    }

    @Test
    fun tetherPreviewPanCannotMoveImageOutsideViewport() {
        assertEquals(
            Pair(400f, -300f),
            clampTetherPan(
                x = 2_000f,
                y = -2_000f,
                zoom = 2f,
                viewportWidth = 1_000,
                viewportHeight = 600,
                imageWidth = 1_200,
                imageHeight = 800
            )
        )
        assertEquals(
            Pair(0f, 0f),
            clampTetherPan(200f, 200f, 1f, 1_000, 600, 1_200, 800)
        )
    }
}
