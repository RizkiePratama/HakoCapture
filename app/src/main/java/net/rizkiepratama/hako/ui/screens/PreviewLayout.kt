package net.rizkiepratama.hako.ui.screens

import kotlin.math.roundToInt

data class PreviewSize(val width: Int, val height: Int)

/** Returns the largest source-aspect-ratio preview that fits inside its container. */
fun previewSizeForContainer(
    containerWidth: Int,
    containerHeight: Int,
    sourceWidth: Int,
    sourceHeight: Int
): PreviewSize {
    require(containerWidth > 0 && containerHeight > 0)
    require(sourceWidth > 0 && sourceHeight > 0)

    val sourceAspectRatio = sourceWidth.toFloat() / sourceHeight
    return if (containerWidth.toFloat() / containerHeight >= sourceAspectRatio) {
        PreviewSize(
            width = (containerHeight * sourceAspectRatio).roundToInt(),
            height = containerHeight
        )
    } else {
        PreviewSize(
            width = containerWidth,
            height = (containerWidth / sourceAspectRatio).roundToInt()
        )
    }
}
