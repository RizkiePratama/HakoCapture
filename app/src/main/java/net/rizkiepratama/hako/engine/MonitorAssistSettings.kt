package net.rizkiepratama.hako.engine

enum class AssistOutput { PREVIEW, RECORDING, ANALYSIS }

data class AssistEffects(val zebra: Boolean = false, val peaking: Boolean = false)

enum class PeakingSensitivity(val label: String, val threshold: Float) {
    LOW("Low", .28f), MEDIUM("Medium", .16f), HIGH("High", .08f)
}

enum class PeakingColor(val label: String, val rgb: Int) {
    RED("Red", 0xFF3030), GREEN("Green", 0x30FF50), CYAN("Cyan", 0x00DDFF), YELLOW("Yellow", 0xFFFF00)
}

data class MonitorAssistSettings(
    val zebraPreview: Boolean = false,
    val zebraRecording: Boolean = false,
    val peakingPreview: Boolean = false,
    val peakingRecording: Boolean = false,
    val zebraLevel: Int = 95,
    val peakingSensitivity: PeakingSensitivity = PeakingSensitivity.MEDIUM,
    val peakingColor: PeakingColor = PeakingColor.RED
) {
    val zebraThreshold: Float get() = zebraLevel.coerceIn(70, 100) / 100f

    fun effectsFor(output: AssistOutput): AssistEffects = when (output) {
        AssistOutput.PREVIEW -> AssistEffects(zebraPreview, peakingPreview)
        AssistOutput.RECORDING -> AssistEffects(zebraRecording, peakingRecording)
        AssistOutput.ANALYSIS -> AssistEffects()
    }
}
