package net.rizkiepratama.hako.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import net.rizkiepratama.hako.engine.MonitorAssistSettings
import net.rizkiepratama.hako.engine.PeakingColor
import net.rizkiepratama.hako.engine.PeakingSensitivity
import kotlin.math.roundToInt

@Composable
internal fun MonitorAssistControls(settings: MonitorAssistSettings, onChange: (MonitorAssistSettings) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Zebra warning", style = MaterialTheme.typography.titleSmall)
        AssistSwitch("Show zebra in preview", settings.zebraPreview) { onChange(settings.copy(zebraPreview = it)) }
        AssistSwitch("Include zebra in recording", settings.zebraRecording) { onChange(settings.copy(zebraRecording = it)) }
        Text("Highlight threshold: ${settings.zebraLevel}% brightness", style = MaterialTheme.typography.bodySmall)
        Slider(value = settings.zebraLevel.toFloat(), valueRange = 70f..100f, steps = 29,
            onValueChange = { onChange(settings.copy(zebraLevel = it.roundToInt())) })
        Text("Stripes mark brightness at or above the threshold.", style = MaterialTheme.typography.bodySmall)
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("Focus peaking", style = MaterialTheme.typography.titleSmall)
        AssistSwitch("Show peaking in preview", settings.peakingPreview) { onChange(settings.copy(peakingPreview = it)) }
        AssistSwitch("Include peaking in recording", settings.peakingRecording) { onChange(settings.copy(peakingRecording = it)) }
        Text("Sensitivity", style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PeakingSensitivity.entries.forEach { sensitivity ->
                FilterChip(selected = settings.peakingSensitivity == sensitivity,
                    onClick = { onChange(settings.copy(peakingSensitivity = sensitivity)) }, label = { Text(sensitivity.label) })
            }
        }
        var colorsExpanded by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { colorsExpanded = true }) { Text("Peaking color: ${settings.peakingColor.label}") }
            DropdownMenu(colorsExpanded, { colorsExpanded = false }) {
                PeakingColor.entries.forEach { color ->
                    DropdownMenuItem(text = { Text(color.label) }, onClick = {
                        colorsExpanded = false
                        onChange(settings.copy(peakingColor = color))
                    })
                }
            }
        }
        Text("Peaking highlights strong edges to help judge focus; high sensitivity also reveals weaker edges and noise.", style = MaterialTheme.typography.bodySmall)
        Text("Recording switches permanently include that aid in the saved video. Leave them off for a clean recording.", style = MaterialTheme.typography.bodySmall,
            color = if (settings.zebraRecording || settings.peakingRecording) Color(0xFFFFB44A) else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AssistSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
