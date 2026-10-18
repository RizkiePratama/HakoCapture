package net.rizkiepratama.hako.engine

/** A capture-card mode advertised through its UVC descriptors. */
data class CaptureMode(
    val width: Int,
    val height: Int,
    val frameRate: Int?,
    val frameFormat: Int
) {
    val label: String
        get() = "$width×$height • ${frameRate?.let { "$it fps" } ?: "Auto fps"}"
}

/** Removes format-level duplicates while keeping the preferred MJPEG mode. */
fun collapseCaptureModes(modes: List<CaptureMode>): List<CaptureMode> =
    modes
        .groupBy { Triple(it.width, it.height, it.frameRate) }
        .values
        .map { candidates ->
            candidates.firstOrNull { it.frameFormat == 1 } ?: candidates.first()
        }
        .sortedWith(
            compareByDescending<CaptureMode> { it.width * it.height }
                .thenByDescending { it.frameRate ?: 0 }
        )
