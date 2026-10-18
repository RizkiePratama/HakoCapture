package net.rizkiepratama.hako.ui.screens

import kotlin.math.max
import kotlin.math.min

fun useLandscapeTetherLayout(width: Int, height: Int): Boolean = width > height

fun useWideLandingLayout(width: Int, height: Int): Boolean = width > height && width >= 720

fun tetherActionImageIds(
    selectionMode: Boolean,
    focusedImageId: String?,
    selectedImageIds: Set<String>
): List<String> = if (selectionMode) selectedImageIds.toList() else listOfNotNull(focusedImageId)

fun clampTetherZoom(zoom: Float): Float = zoom.coerceIn(1f, 5f)

fun tetherZoomAfterDoubleTap(currentZoom: Float): Float = if (currentZoom > 1f) 1f else 2f

fun clampTetherPan(
    x: Float,
    y: Float,
    zoom: Float,
    viewportWidth: Int,
    viewportHeight: Int,
    imageWidth: Int,
    imageHeight: Int
): Pair<Float, Float> {
    if (viewportWidth <= 0 || viewportHeight <= 0 || imageWidth <= 0 || imageHeight <= 0) {
        return 0f to 0f
    }
    val fitScale = min(
        viewportWidth.toFloat() / imageWidth,
        viewportHeight.toFloat() / imageHeight
    )
    val maxX = max(0f, (imageWidth * fitScale * zoom - viewportWidth) / 2f)
    val maxY = max(0f, (imageHeight * fitScale * zoom - viewportHeight) / 2f)
    return x.coerceIn(-maxX, maxX) to y.coerceIn(-maxY, maxY)
}
