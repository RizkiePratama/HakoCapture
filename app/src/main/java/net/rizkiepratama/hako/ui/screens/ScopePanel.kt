package net.rizkiepratama.hako.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.rizkiepratama.hako.engine.ScopeFrame
import net.rizkiepratama.hako.engine.ScopeRaster
import kotlin.math.cos
import kotlin.math.sin

private val ScopeGrid = Color(0xFF857637)
private val TraceColors = listOf(Color(0xFFFF5757), Color(0xFF52E875), Color(0xFF508FFF))

@Composable
internal fun ScopeDock(
    settings: ScopeVisibility,
    frame: ScopeFrame?,
    columns: Int,
    onChange: (ScopeVisibility) -> Unit,
    onSettings: () -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier
) {
    var strip by remember { mutableStateOf(false) }
    Column(modifier.background(Color(0xFF141416))) {
        Row(Modifier.fillMaxWidth().height(40.dp).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("SCOPES", color = Color(0xFFB5B5BC), fontSize = 10.sp)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { strip = false }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text("ALL", color = if (!strip) Color(0xFF6DE7E1) else Color.Gray, fontSize = 11.sp)
            }
            TextButton(onClick = { strip = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text("STRIP", color = if (strip) Color(0xFF6DE7E1) else Color.Gray, fontSize = 11.sp)
            }
            IconButton(onClick = onSettings, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Tune, "Configure scopes", tint = Color(0xFFCCCCD2), modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onHide, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Close, "Hide scopes", tint = Color(0xFFCCCCD2), modifier = Modifier.size(18.dp))
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f).padding(2.dp)) {
            val types = settings.visibleTypes()
            if (strip) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(types, key = { it.name }) { type ->
                        ScopePanel(type, settings, frame, onChange, Modifier.width(280.dp).fillMaxHeight())
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    types.chunked(columns).forEach { row ->
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            row.forEach { type -> ScopePanel(type, settings, frame, onChange, Modifier.weight(1f).fillMaxHeight()) }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ScopePanel(type: ScopeType, settings: ScopeVisibility, frame: ScopeFrame?, onChange: (ScopeVisibility) -> Unit, modifier: Modifier, overlay: Boolean = false) {
    val split = when (type) {
        ScopeType.RGB_PARADE -> settings.rgbSplit
        ScopeType.YUV_PARADE -> settings.yuvSplit
        ScopeType.COLOR_HISTOGRAM -> settings.histogramSplit
        else -> false
    }
    val configurable = type == ScopeType.RGB_PARADE || type == ScopeType.YUV_PARADE || type == ScopeType.COLOR_HISTOGRAM
    var menu by remember { mutableStateOf(false) }
    Column(modifier.background(Color.Black.copy(alpha = if (overlay) .58f else 1f))) {
        Row(Modifier.fillMaxWidth().height(32.dp).background(Color(0xFF242427).copy(alpha = if (overlay) .8f else 1f)).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(when (type) {
                ScopeType.RGB_PARADE -> if (split) "RGB Parade" else "RGB Waveform"
                ScopeType.YUV_PARADE -> if (split) "YUV Parade" else "YUV Waveform"
                ScopeType.LUMA -> "Waveform Y"
                ScopeType.HISTOGRAM -> "Histogram Y"
                ScopeType.COLOR_HISTOGRAM -> "Histogram RGB"
                else -> type.title
            }, color = Color(0xFFD7D7DC), fontSize = 10.sp, maxLines = 1, modifier = Modifier.weight(1f))
            if (overlay) {
                IconButton(onClick = { onChange(settings.copy(pinnedScope = null)) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, "Unpin scope", tint = Color(0xFF6DE7E1), modifier = Modifier.size(16.dp))
                }
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ArrowDropDown, if (configurable) "Channel layout" else "Scope options", tint = Color(0xFFBBBBBF), modifier = Modifier.size(18.dp))
                }
                DropdownMenu(menu, { menu = false }) {
                    if (configurable) listOf(false, true).forEach { value ->
                        DropdownMenuItem(text = { Text((if (value) "Split" else "Combined") + if (value == split) " ✓" else "") }, onClick = {
                            onChange(when (type) {
                                ScopeType.RGB_PARADE -> settings.copy(rgbSplit = value)
                                ScopeType.YUV_PARADE -> settings.copy(yuvSplit = value)
                                else -> settings.copy(histogramSplit = value)
                            }); menu = false
                        })
                    }
                    DropdownMenuItem(text = { Text(if (settings.pinnedScope == type) "Unpin from preview" else "Pin over preview") }, onClick = {
                        onChange(if (settings.pinnedScope == type) settings.copy(pinnedScope = null) else settings.pin(type))
                        menu = false
                    })
                }
            }
        }
        ScopePlot(type, settings, frame, Modifier.fillMaxWidth().weight(1f), overlay)
    }
}

@Composable
private fun ScopePlot(type: ScopeType, settings: ScopeVisibility, frame: ScopeFrame?, modifier: Modifier, overlay: Boolean) {
    val raster = when (type) {
        ScopeType.RGB_PARADE -> if (settings.rgbSplit) frame?.rgbSplit else frame?.rgbJoined
        ScopeType.YUV_PARADE -> if (settings.yuvSplit) frame?.yuvSplit else frame?.yuvJoined
        ScopeType.LUMA -> frame?.lumaTrace
        ScopeType.VECTORSCOPE -> frame?.vectorTrace
        else -> null
    }
    val image = remember(raster, overlay) { raster?.toImage(overlay) }
    Canvas(modifier) {
        if (size.width < 44.dp.toPx() || size.height < 32.dp.toPx()) return@Canvas
        if (type == ScopeType.VECTORSCOPE) {
            drawVector(image)
        } else {
            val histogram = type == ScopeType.HISTOGRAM || type == ScopeType.COLOR_HISTOGRAM
            inset(left = 26.dp.toPx(), top = if (histogram) 16.dp.toPx() else 7.dp.toPx(), right = 9.dp.toPx(), bottom = 8.dp.toPx()) {
                if (histogram) {
                    drawHistogram(frame, type == ScopeType.COLOR_HISTOGRAM, settings.histogramSplit)
                    drawScale(horizontal = true)
                } else {
                    image?.let { drawImage(it, dstSize = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1)), filterQuality = FilterQuality.Low) }
                    drawScale(horizontal = false)
                    val split = (type == ScopeType.RGB_PARADE && settings.rgbSplit) || (type == ScopeType.YUV_PARADE && settings.yuvSplit)
                    if (split) {
                        for (i in 1..2) drawLine(Color(0xFF3A3A3D), Offset(size.width * i / 3, 0f), Offset(size.width * i / 3, size.height))
                    }
                }
            }
        }
    }
}

private fun ScopeRaster.toImage(transparent: Boolean): ImageBitmap {
    val colors = if (!transparent) pixels else IntArray(pixels.size) { index ->
        val pixel = pixels[index]
        val r = (pixel shr 16) and 255
        val g = (pixel shr 8) and 255
        val b = pixel and 255
        val alpha = maxOf(r, g, b)
        // Preserve trace brightness while letting the live image show through empty/dim bins.
        if (alpha == 0) 0 else (alpha shl 24) or ((r * 255 / alpha) shl 16) or
            ((g * 255 / alpha) shl 8) or (b * 255 / alpha)
    }
    return Bitmap.createBitmap(colors, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
}

private fun DrawScope.drawScale(horizontal: Boolean) {
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(155, 139, 68)
        textSize = 7.dp.toPx()
    }
    // The analyzed TextureView is 8-bit; label its actual range rather than claiming 10-bit data.
    for (i in 0..4) {
        val fraction = i / 4f
        val label = (fraction * 255).toInt().toString()
        if (horizontal) {
            val x = fraction * size.width
            drawLine(ScopeGrid.copy(alpha = .38f), Offset(x, 0f), Offset(x, size.height), .5.dp.toPx())
            drawContext.canvas.nativeCanvas.drawText(label, x - paint.measureText(label) / 2, -4.dp.toPx(), paint)
        } else {
            val y = size.height * (1 - fraction)
            drawLine(ScopeGrid.copy(alpha = .38f), Offset(0f, y), Offset(size.width, y), .5.dp.toPx())
            drawContext.canvas.nativeCanvas.drawText(label, -4.dp.toPx() - paint.measureText(label), (y + paint.textSize / 3).coerceIn(paint.textSize, size.height), paint)
        }
    }
}

private fun DrawScope.drawHistogram(frame: ScopeFrame?, color: Boolean, split: Boolean) {
    val data = if (color) listOf(frame?.redHistogramNormalized, frame?.greenHistogramNormalized, frame?.blueHistogramNormalized) else listOf(frame?.lumaHistogramNormalized)
    val lanes = if (color && split) 3 else 1
    val laneHeight = size.height / lanes
    data.forEachIndexed { channel, values ->
        val top = if (lanes == 3) channel * laneHeight else 0f
        val bottom = top + laneHeight
        val path = Path()
        // Include every bin, including isolated clipping spikes at 0 and 255.
        for (i in 0..255) {
            val x = size.width * i / 255f
            val y = bottom - (values?.get(i) ?: 0f) * (laneHeight - 2.dp.toPx()).coerceAtLeast(0f)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val tint = if (color) TraceColors[channel] else Color(0xFFD9E3DF)
        drawPath(path, tint.copy(alpha = .9f), style = Stroke(.7.dp.toPx()))
        path.lineTo(size.width, bottom); path.lineTo(0f, bottom); path.close()
        drawPath(path, tint.copy(alpha = .18f), blendMode = BlendMode.Plus)
        if (lanes == 3) drawLine(Color(0xFF353538), Offset(0f, bottom), Offset(size.width, bottom), .5.dp.toPx())
    }
}

private fun DrawScope.drawVector(image: ImageBitmap?) {
    val radius = (size.minDimension / 2 - 10.dp.toPx()).coerceAtLeast(1f)
    val center = Offset(size.width / 2, size.height / 2)
    image?.let {
        drawImage(it, dstOffset = IntOffset((center.x - radius).toInt(), (center.y - radius).toInt()), dstSize = IntSize((radius * 2).toInt().coerceAtLeast(1), (radius * 2).toInt().coerceAtLeast(1)), filterQuality = FilterQuality.Low)
    }
    val graticule = Color(0xFF76756B).copy(alpha = .55f)
    drawCircle(graticule, radius, center, style = Stroke(.5.dp.toPx()))
    drawLine(graticule, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), .5.dp.toPx())
    drawLine(graticule, Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), .5.dp.toPx())
    for (i in 0 until 72) {
        val angle = i * Math.PI.toFloat() / 36
        val unit = Offset(cos(angle), sin(angle))
        drawLine(graticule, center + unit * radius, center + unit * (radius * if (i % 6 == 0) .94f else .975f), .5.dp.toPx())
    }
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(151, 147, 128); textSize = 7.dp.toPx() }
    // Targets use the same Rec.709 chroma transform as the samples (75% saturation).
    listOf("R" to 0xff0000, "Y" to 0xffff00, "G" to 0x00ff00, "C" to 0x00ffff, "B" to 0x0000ff, "M" to 0xff00ff).forEach { (label, rgb) ->
        val r = ((rgb shr 16) and 255) / 255f
        val g = ((rgb shr 8) and 255) / 255f
        val b = (rgb and 255) / 255f
        val cb = -.1146f * r - .3854f * g + .5f * b
        val cr = .5f * r - .4542f * g - .0458f * b
        val target = center + Offset(cb, -cr) * (radius * 1.5f)
        val half = 2.dp.toPx()
        drawRect(graticule, target - Offset(half, half), Size(half * 2, half * 2), style = Stroke(.6.dp.toPx()))
        drawContext.canvas.nativeCanvas.drawText(label, target.x + half * 2, target.y + half, paint)
    }
}

