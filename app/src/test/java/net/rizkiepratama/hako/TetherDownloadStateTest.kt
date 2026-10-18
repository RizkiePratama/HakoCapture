package net.rizkiepratama.hako

import net.rizkiepratama.hako.ui.screens.isActiveTetherDownload
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TetherDownloadStateTest {
    @Test
    fun zeroTotalClearsInterruptedDownload() {
        assertFalse(isActiveTetherDownload(0, 0))
        assertTrue(isActiveTetherDownload(1, 10))
    }
}
