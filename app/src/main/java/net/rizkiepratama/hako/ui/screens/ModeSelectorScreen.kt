package net.rizkiepratama.hako.ui.screens

import android.hardware.usb.UsbDevice
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val BackgroundColor = Color(0xFF080A0D)
private val CardBackground = Color(0xD914181E)
private val AccentCyan = Color(0xFF6DE7E1)
private val AccentAmber = Color(0xFFFFC857)

private enum class TutorialMode {
    MONITOR,
    TETHER
}

@Composable
fun ModeSelectorScreen(
    permissionsGranted: Boolean,
    attachedDevices: List<UsbDevice>,
    onRequestPermissions: () -> Unit,
    onSelectMonitor: () -> Unit,
    onSelectTether: () -> Unit
) {
    var activeTutorial by remember { mutableStateOf<TutorialMode?>(null) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        val wideLayout = useWideLandingLayout(maxWidth.value.toInt(), maxHeight.value.toInt())
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Header
            Text(
                text = "HAKO CAPTURE",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "Select Capture & Review Mode",
                color = Color(0xFF89939D),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 4.dp, bottom = if (wideLayout) 28.dp else 20.dp)
            )

            val monitorCard: @Composable (Modifier) -> Unit = { modifier ->
                ModeCard(
                    title = "LIVE MONITOR",
                    subtitle = "UVC / HDMI Input",
                    description = "Real-time HDMI preview, waveform/vectorscope/histogram analysis, false color, peaking & MP4 recording.",
                    icon = Icons.Default.Monitor,
                    accentColor = AccentCyan,
                    onClick = onSelectMonitor,
                    onOpenGuide = { activeTutorial = TutorialMode.MONITOR },
                    modifier = modifier
                )
            }
            val tetherCard: @Composable (Modifier) -> Unit = { modifier ->
                ModeCard(
                    title = "CAMERA TETHERING",
                    subtitle = "Sony USB (PC Remote)",
                    description = "Instant photo pulling on shutter release. Supports high-res JPEGs and embedded RAW previews for client review.",
                    icon = Icons.Default.CameraAlt,
                    accentColor = AccentAmber,
                    onClick = onSelectTether,
                    onOpenGuide = { activeTutorial = TutorialMode.TETHER },
                    modifier = modifier
                )
            }

            if (wideLayout) {
                Row(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    monitorCard(Modifier.weight(1f))
                    tetherCard(Modifier.weight(1f))
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    monitorCard(Modifier.fillMaxWidth())
                    tetherCard(Modifier.fillMaxWidth())
                }
            }

            Spacer(Modifier.height(if (wideLayout) 28.dp else 16.dp))

            // USB Status Footer
            Surface(
                modifier = Modifier.fillMaxWidth().widthIn(max = 700.dp),
                color = CardBackground,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Usb,
                        contentDescription = "USB",
                        tint = if (attachedDevices.isNotEmpty()) AccentCyan else Color(0xFF89939D)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (attachedDevices.isNotEmpty()) {
                                "Connected USB Device: ${attachedDevices.first().productName ?: attachedDevices.first().deviceName}"
                            } else {
                                "No USB Camera / Input Device Detected"
                            },
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (!permissionsGranted) "USB permissions required for camera access." else "Ready to launch mode.",
                            color = Color(0xFF89939D),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    if (!permissionsGranted) {
                        Button(
                            onClick = onRequestPermissions,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = Color.Black),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("GRANT PERMISSION")
                        }
                    } else if (attachedDevices.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Ready",
                            tint = AccentCyan
                        )
                    }
                }
            }
        }
    }

    // Popup Tutorials
    when (activeTutorial) {
        TutorialMode.MONITOR -> {
            SetupTutorialDialog(
                title = "Live Monitor Setup Guide",
                accentColor = AccentCyan,
                hardwareRequirement = "HDMI Cable + USB Video Capture Card (UVC / HDMI to USB-C).",
                steps = listOf(
                    "Connect camera HDMI output to the USB capture card.",
                    "Plug capture card USB connector into your Android tablet/phone.",
                    "In Camera Menu: Set 'HDMI Info Display' to OFF for a clean video feed.",
                    "In Camera Menu: Set 'HDMI Resolution' to 1080p or 4K.",
                    "Turn OFF Auto Power Off / Sleep Timer on camera to avoid signal drops.",
                    "Tap LAUNCH MODE and grant USB permission when prompted."
                ),
                onDismiss = { activeTutorial = null },
                onLaunch = {
                    activeTutorial = null
                    onSelectMonitor()
                }
            )
        }
        TutorialMode.TETHER -> {
            SetupTutorialDialog(
                title = "Sony Camera Tethering Setup Guide",
                accentColor = AccentAmber,
                hardwareRequirement = "Micro-USB or USB-C cable (1-2m) directly connecting Sony camera to Android tablet/phone.",
                steps = listOf(
                    "MENU > Setup > USB Connection: Set to 'PC Remote' (Crucial: Do NOT select Mass Storage or MTP).",
                    "MENU > Setup > USB Power Supply: Set to 'Off' (Prevents camera from draining tablet battery).",
                    "MENU > Setup > PC Remote Settings: Set save destination to 'PC + Camera' or 'PC Only'.",
                    "Connect USB cable between camera and tablet.",
                    "Tap LAUNCH MODE and grant USB permission when prompted.",
                    "Fire shutter on camera: Hako Capture will automatically pull and display newest JPEG or RAW preview!"
                ),
                onDismiss = { activeTutorial = null },
                onLaunch = {
                    activeTutorial = null
                    onSelectTether()
                }
            )
        }
        null -> {}
    }
}

@Composable
private fun ModeCard(
    title: String,
    subtitle: String,
    description: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    onOpenGuide: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color = CardBackground,
        tonalElevation = 2.dp,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Surface(
                    color = accentColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text = subtitle.uppercase(),
                        color = accentColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = description,
                color = Color(0xFFAFB7BE),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.heightIn(min = 60.dp)
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onClick,
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color.Black),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("LAUNCH", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onOpenGuide,
                    modifier = Modifier.wrapContentWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFF384350))
                ) {
                    Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Guide", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("GUIDE")
                }
            }
        }
    }
}

@Composable
private fun SetupTutorialDialog(
    title: String,
    accentColor: Color,
    hardwareRequirement: String,
    steps: List<String>,
    onDismiss: () -> Unit,
    onLaunch: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(accentColor)
                )
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Hardware Section
                Surface(
                    color = accentColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("HARDWARE REQUIRED", color = accentColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(hardwareRequirement, color = Color.White, style = MaterialTheme.typography.bodySmall)
                    }
                }

                HorizontalDivider(color = Color(0xFF262D37))

                Text("CAMERA CONFIGURATION STEPS", color = Color(0xFF89939D), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)

                steps.forEachIndexed { index, step ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Surface(
                            color = Color(0xFF262D37),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "${index + 1}",
                                color = accentColor,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        Text(step, color = Color(0xFFCAD3DA), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onLaunch,
                colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color.Black)
            ) {
                Text("LAUNCH MODE", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CLOSE", color = Color(0xFFAFB7BE))
            }
        },
        containerColor = CardBackground,
        shape = RoundedCornerShape(16.dp)
    )
}
