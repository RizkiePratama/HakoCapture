package net.rizkiepratama.hako.engine

import kotlin.math.exp
import kotlin.math.roundToInt

data class ScopeRaster(val width: Int, val height: Int, val pixels: IntArray)

/** Rasterize on the analysis worker, rather than drawing thousands of rectangles on the UI thread.
 * A narrow reconstruction kernel keeps isolated samples visible without turning them into tiles.
 * Exponential exposure retains faint traces and rolls dense regions gently toward white.
 */
fun renderDensityTrace(
    density: IntArray,
    columns: Int,
    channels: Int,
    split: Boolean,
    colors: List<Int>,
    samplesPerColumn: Int
): ScopeRaster {
    val width = columns * if (split) channels else 1
    val pixels = IntArray(width * SCOPE_LEVELS) { 0xff000000.toInt() }
    val exposure = 24f / samplesPerColumn.coerceAtLeast(1)
    repeat(channels) { channel ->
        val base = channel * columns * SCOPE_LEVELS
        val color = colors[channel]
        for (x in 0 until columns) {
            for (y in 0 until SCOPE_LEVELS) {
                val center = density[base + x * SCOPE_LEVELS + y].toFloat()
                val below = if (y > 0) density[base + x * SCOPE_LEVELS + y - 1] else 0
                val above = if (y < SCOPE_LEVELS - 1) density[base + x * SCOPE_LEVELS + y + 1] else 0
                val left = if (x > 0) density[base + (x - 1) * SCOPE_LEVELS + y] else 0
                val right = if (x < columns - 1) density[base + (x + 1) * SCOPE_LEVELS + y] else 0
                val amount = center + .18f * (below + above) + .08f * (left + right)
                if (amount == 0f) continue
                val light = 1f - exp(-amount * exposure)
                val index = (SCOPE_LEVELS - 1 - y) * width + x + if (split) channel * columns else 0
                val old = pixels[index]
                val r = (((old shr 16) and 255) + ((color shr 16) and 255) * light).roundToInt().coerceAtMost(255)
                val g = (((old shr 8) and 255) + ((color shr 8) and 255) * light).roundToInt().coerceAtMost(255)
                val b = ((old and 255) + (color and 255) * light).roundToInt().coerceAtMost(255)
                pixels[index] = (0xff shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
    }
    return ScopeRaster(width, SCOPE_LEVELS, pixels)
}

fun renderVectorTrace(density: IntArray): ScopeRaster {
    val pixels = IntArray(VECTOR_SIZE * VECTOR_SIZE) { 0xff000000.toInt() }
    for (v in 0 until VECTOR_SIZE) {
        for (u in 0 until VECTOR_SIZE) {
            val i = v * VECTOR_SIZE + u
            val count = density[i].toFloat() +
                .15f * ((if (u > 0) density[i - 1] else 0) + (if (u < VECTOR_SIZE - 1) density[i + 1] else 0) +
                    (if (v > 0) density[i - VECTOR_SIZE] else 0) + (if (v < VECTOR_SIZE - 1) density[i + VECTOR_SIZE] else 0))
            if (count == 0f) continue
            val cb = u / 255f - .5f
            val cr = v / 255f - .5f
            val r = (.5f + 1.5748f * cr).coerceIn(0f, 1f)
            val g = (.5f - .1873f * cb - .4681f * cr).coerceIn(0f, 1f)
            val b = (.5f + 1.8556f * cb).coerceIn(0f, 1f)
            val peak = maxOf(r, g, b, .001f)
            val light = 1f - exp(-count * .12f)
            // Cr is positive upward; Cb is positive to the right, including target markers.
            pixels[(VECTOR_SIZE - 1 - v) * VECTOR_SIZE + u] = (0xff shl 24) or
                ((r / peak * light * 255).roundToInt() shl 16) or
                ((g / peak * light * 255).roundToInt() shl 8) or (b / peak * light * 255).roundToInt()
        }
    }
    return ScopeRaster(VECTOR_SIZE, VECTOR_SIZE, pixels)
}
