package net.rizkiepratama.hako

import net.rizkiepratama.hako.engine.AssistOutput
import net.rizkiepratama.hako.engine.MonitorAssistSettings
import org.junit.Assert.*
import org.junit.Test

class MonitorAssistSettingsTest {
    @Test fun enablingPreviewAidsDoesNotBurnThemIntoRecordings() {
        val settings = MonitorAssistSettings(zebraPreview = true, peakingPreview = true)
        assertTrue(settings.effectsFor(AssistOutput.PREVIEW).zebra)
        assertTrue(settings.effectsFor(AssistOutput.PREVIEW).peaking)
        assertFalse(settings.effectsFor(AssistOutput.RECORDING).zebra)
        assertFalse(settings.effectsFor(AssistOutput.RECORDING).peaking)
    }

    @Test fun recordingOptionsAreIndependentForEachAidAndFromPreview() {
        val zebra = MonitorAssistSettings(zebraRecording = true)
        assertTrue(zebra.effectsFor(AssistOutput.RECORDING).zebra)
        assertFalse(zebra.effectsFor(AssistOutput.RECORDING).peaking)
        assertFalse(zebra.effectsFor(AssistOutput.PREVIEW).zebra)
        val peaking = MonitorAssistSettings(peakingRecording = true)
        assertTrue(peaking.effectsFor(AssistOutput.RECORDING).peaking)
        assertFalse(peaking.effectsFor(AssistOutput.RECORDING).zebra)
    }

    @Test fun analysisNeverIncludesAidsEvenWhenBothOutputsUseThem() {
        val settings = MonitorAssistSettings(true, true, true, true)
        assertFalse(settings.effectsFor(AssistOutput.ANALYSIS).zebra)
        assertFalse(settings.effectsFor(AssistOutput.ANALYSIS).peaking)
    }

    @Test fun zebraThresholdIsNormalizedAndClampedToItsSupportedRange() {
        assertEquals(.95f, MonitorAssistSettings().zebraThreshold, .0001f)
        assertEquals(.7f, MonitorAssistSettings(zebraLevel = -5).zebraThreshold, .0001f)
        assertEquals(1f, MonitorAssistSettings(zebraLevel = 150).zebraThreshold, .0001f)
    }
}
