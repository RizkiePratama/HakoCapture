package net.rizkiepratama.hako.engine

import kotlin.math.roundToInt

const val SCOPE_COLUMNS = 320
const val SCOPE_LEVELS = 256
const val VECTOR_SIZE = 256

/** Immutable snapshots; density is column-major, with channel lanes stored consecutively. */
data class ScopeFrame(
    val width: Int,
    val height: Int,
    val redHistogram: IntArray,
    val greenHistogram: IntArray,
    val blueHistogram: IntArray,
    val lumaHistogram: IntArray,
    val vectorDensity: IntArray,
    val redHistogramNormalized: FloatArray,
    val greenHistogramNormalized: FloatArray,
    val blueHistogramNormalized: FloatArray,
    val lumaHistogramNormalized: FloatArray,
    val yuvParadeDensity: IntArray,
    val rgbParadeDensity: IntArray,
    val lumaDensity: IntArray,
    val rgbJoined: ScopeRaster,
    val rgbSplit: ScopeRaster,
    val yuvJoined: ScopeRaster,
    val yuvSplit: ScopeRaster,
    val lumaTrace: ScopeRaster,
    val vectorTrace: ScopeRaster
)

fun analyzeRgbaFrame(rgba: ByteArray, width: Int, height: Int): ScopeFrame {
    require(width > 0 && height > 0)
    require(rgba.size.toLong() >= width.toLong() * height * 4)
    val red = IntArray(256)
    val green = IntArray(256)
    val blue = IntArray(256)
    val luma = IntArray(256)
    val vectors = IntArray(VECTOR_SIZE * VECTOR_SIZE)
    val laneSize = SCOPE_COLUMNS * SCOPE_LEVELS
    val yuvDensity = IntArray(3 * laneSize)
    val rgbDensity = IntArray(3 * laneSize)
    val lumaDensity = IntArray(laneSize)
    var offset = 0
    repeat(width * height) { pixelIndex ->
        val r = rgba[offset].toInt() and 0xff
        val g = rgba[offset + 1].toInt() and 0xff
        val b = rgba[offset + 2].toInt() and 0xff
        offset += 4
        red[r]++; green[g]++; blue[b]++
        val y = (0.2126f * r + 0.7152f * g + 0.0722f * b).roundToInt().coerceIn(0, 255)
        luma[y]++
        // Chroma is already biased to [0, 1]. Do not add a second 0.5 offset.
        val u = (-0.1146f * r - 0.3854f * g + 0.5f * b) / 255f + 0.5f
        val v = (0.5f * r - 0.4542f * g - 0.0458f * b) / 255f + 0.5f
        val ub = (u * 255).roundToInt().coerceIn(0, 255)
        val vb = (v * 255).roundToInt().coerceIn(0, 255)
        vectors[vb * VECTOR_SIZE + ub]++
        val column = (pixelIndex % width) * SCOPE_COLUMNS / width
        val base = column * SCOPE_LEVELS
        rgbDensity[base + r]++
        rgbDensity[laneSize + base + g]++
        rgbDensity[2 * laneSize + base + b]++
        yuvDensity[base + y]++
        yuvDensity[laneSize + base + ub]++
        yuvDensity[2 * laneSize + base + vb]++
        lumaDensity[base + y]++
    }
    // A shared RGB scale preserves the relative populations of the three channels.
    val rgbPeak = maxOf(red.maxOrNull() ?: 1, green.maxOrNull() ?: 1, blue.maxOrNull() ?: 1)
    val samplesPerColumn = (width * height / SCOPE_COLUMNS).coerceAtLeast(1)
    val rgbColors = listOf(0xff4444, 0x44ff66, 0x5577ff)
    val yuvColors = listOf(0xffffff, 0x55bbff, 0xff66aa)
    return ScopeFrame(
        width, height, red, green, blue, luma, vectors,
        normalize(red, rgbPeak), normalize(green, rgbPeak), normalize(blue, rgbPeak), normalize(luma),
        yuvDensity, rgbDensity, lumaDensity,
        renderDensityTrace(rgbDensity, SCOPE_COLUMNS, 3, false, rgbColors, samplesPerColumn),
        renderDensityTrace(rgbDensity, SCOPE_COLUMNS, 3, true, rgbColors, samplesPerColumn),
        renderDensityTrace(yuvDensity, SCOPE_COLUMNS, 3, false, yuvColors, samplesPerColumn),
        renderDensityTrace(yuvDensity, SCOPE_COLUMNS, 3, true, yuvColors, samplesPerColumn),
        renderDensityTrace(lumaDensity, SCOPE_COLUMNS, 1, false, listOf(0xe5f0ed), samplesPerColumn),
        renderVectorTrace(vectors)
    )
}

private fun normalize(values: IntArray, peak: Int = values.maxOrNull() ?: 1): FloatArray =
    FloatArray(values.size) { values[it].toFloat() / peak.coerceAtLeast(1) }
