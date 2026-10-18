package net.rizkiepratama.hako

import net.rizkiepratama.hako.ui.screens.ScopeType
import net.rizkiepratama.hako.ui.screens.ScopeVisibility
import net.rizkiepratama.hako.ui.screens.scopeToggleLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScopeModelTest {
    @Test
    fun pinningAnotherScopeReplacesTheSingleOverlay() {
        val settings = ScopeVisibility().pin(ScopeType.RGB_PARADE).pin(ScopeType.VECTORSCOPE)
        assertEquals(ScopeType.VECTORSCOPE, settings.pinnedScope)
    }

    @Test
    fun pinnedScopeKeepsAnalysisRunningWhenDockIsClosed() {
        val settings = ScopeVisibility().pin(ScopeType.LUMA)
        assertTrue(settings.needsAnalysis(dockExpanded = false))
        assertFalse(settings.copy(pinnedScope = null).needsAnalysis(dockExpanded = false))
    }

    @Test
    fun defaultsShowTheCoreExposureScopes() {
        val settings = ScopeVisibility()

        assertTrue(settings.isVisible(ScopeType.HISTOGRAM))
        assertTrue(settings.isVisible(ScopeType.RGB_PARADE))
        assertTrue(settings.isVisible(ScopeType.VECTORSCOPE))
        assertTrue(settings.isVisible(ScopeType.LUMA))
        assertEquals("ALL SCOPES", scopeToggleLabel(settings))
    }

    @Test
    fun eachScopeCanBeToggledIndependently() {
        val settings = ScopeVisibility().toggle(ScopeType.VECTORSCOPE)

        assertFalse(settings.isVisible(ScopeType.VECTORSCOPE))
        assertTrue(settings.isVisible(ScopeType.HISTOGRAM))
        assertTrue(settings.isVisible(ScopeType.RGB_PARADE))
    }

    @Test
    fun allScopesLabelAppearsWhenEveryScopeIsEnabled() {
        val settings = ScopeVisibility().toggle(ScopeType.COLOR_HISTOGRAM)

        assertEquals("5 SCOPES", scopeToggleLabel(settings))
    }
}
