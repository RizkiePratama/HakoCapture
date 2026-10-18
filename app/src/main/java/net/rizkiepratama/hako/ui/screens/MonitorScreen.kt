package net.rizkiepratama.hako.ui.screens

import android.app.Activity
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.StackedLineChart
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.rizkiepratama.hako.engine.*
import android.view.Surface
import android.view.TextureView
import android.os.Environment
import android.media.MediaScannerConnection
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val MonitorBackground = Color(0xFF080A0D)
private val PanelBackground = Color(0xD914181E)
private val AccentCyan = Color(0xFF6DE7E1)
private val AccentAmber = Color(0xFFFFC857)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorScreen(permissionsGranted: Boolean, usbAttachVersion: Int, requestPermissions: () -> Unit) {
    var isRecording by remember { mutableStateOf(false) }
    var recordingSettings by remember { mutableStateOf(RecordingSettings()) }
    var scopeSettings by remember { mutableStateOf(ScopeVisibility()) }
    var assistSettings by remember { mutableStateOf(MonitorAssistSettings()) }
    var showScopeSettings by remember { mutableStateOf(false) }
    var scopesExpanded by remember { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var statusMessage by remember { mutableStateOf("Searching for HDMI input…") }
    var isCameraActive by remember { mutableStateOf(false) }
    var captureWidth by remember { mutableIntStateOf(1280) }
    var captureHeight by remember { mutableIntStateOf(720) }
    var currentCaptureMode by remember { mutableStateOf<CaptureMode?>(null) }
    var captureModes by remember { mutableStateOf(emptyList<CaptureMode>()) }
    var scopeFrame by remember { mutableStateOf<ScopeFrame?>(null) }
    var previewSurfaceReady by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val localView = LocalView.current

    val recorder = remember(context) { Recorder(context) }
    var currentRecordingFile by remember { mutableStateOf<File?>(null) }

    DisposableEffect(localView, isLandscape) {
        (localView.context as? Activity)?.let { activity ->
            val insetsController = WindowCompat.getInsetsController(activity.window, localView)
            if (isLandscape) {
                insetsController.hide(WindowInsetsCompat.Type.statusBars())
            } else {
                insetsController.show(WindowInsetsCompat.Type.statusBars())
            }
        }
        onDispose { }
    }

    val frameAnalyzer = remember {
        FrameAnalyzer { frame ->
            scopeFrame = frame
        }
    }

    val uvcManager = remember {
        UvcCameraManager(
            context = context,
            onDeviceAttached = { Log.d("MonitorScreen", "Attached: ${it.deviceName}") },
            onDeviceDetached = {
                isCameraActive = false
                statusMessage = "HDMI input disconnected"
                currentCaptureMode = null
                captureModes = emptyList()
            },
            onCameraStateChanged = { active, msg ->
                isCameraActive = active
                statusMessage = if (active) "Live input" else msg
            },
            onPreviewModeChanged = { mode ->
                currentCaptureMode = mode
                captureWidth = mode.width
                captureHeight = mode.height
            },
            onCaptureModesChanged = { modes ->
                captureModes = modes
                if (currentCaptureMode == null) currentCaptureMode = modes.firstOrNull()
            },
            onScopePixels = frameAnalyzer::analyzeRgba
        )
    }

    fun startRecording() {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "HAKO_$timeStamp.mp4"
        // Use Pictures or Movies directory for better visibility
        val storageDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "HakoCapture")
        if (!storageDir.exists()) {
            if (!storageDir.mkdirs()) {
                Log.e("MonitorScreen", "Failed to create directory: ${storageDir.absolutePath}")
            }
        }
        
        val file = File(storageDir, fileName)
        currentRecordingFile = file

        val finalSettings = recordingSettings.copy(
            resolution = currentCaptureMode?.let { it.width to it.height } ?: recordingSettings.resolution
        )

        recorder.start(file, finalSettings)
        recorder.getInputSurface()?.let { surface ->
            uvcManager.startCapture(surface)
        }
        isRecording = true
    }

    fun stopRecording() {
        recorder.getInputSurface()?.let { surface ->
            uvcManager.stopCapture(surface)
        }
        recorder.stop()
        isRecording = false

        currentRecordingFile?.let { file ->
            Log.d("MonitorScreen", "Recording finished: ${file.absolutePath}, size: ${file.length()}")
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("video/mp4")) { path, uri ->
                Log.i("MonitorScreen", "Media scanner scanned $path to $uri")
            }
        }
    }

    DisposableEffect(Unit) {
        uvcManager.register()
        onDispose {
            frameAnalyzer.release()
            uvcManager.unregister()
            recorder.stop()
        }
    }

    val analyzeScopes = scopeSettings.needsAnalysis(scopesExpanded)
    LaunchedEffect(isCameraActive, previewSurfaceReady, analyzeScopes) {
        uvcManager.setScopeAnalysisEnabled(isCameraActive && previewSurfaceReady && analyzeScopes)
    }
    LaunchedEffect(assistSettings) {
        uvcManager.setMonitorAssists(assistSettings)
    }

    LaunchedEffect(permissionsGranted, usbAttachVersion) {
        if (permissionsGranted) {
            val device = uvcManager.getUsbDevices().firstOrNull()
            if (device != null) uvcManager.requestPermission(device)
            else statusMessage = "Searching for HDMI input…"
        } else {
            uvcManager.closeCamera()
            statusMessage = "Permissions are required for USB video and audio."
        }
    }

    val changeScopeSettings: (ScopeVisibility) -> Unit = { updated ->
        if (updated.pinnedScope != null && updated.pinnedScope != scopeSettings.pinnedScope) scopesExpanded = false
        scopeSettings = updated
    }
    MonitorWorkspace(
        landscape = isLandscape,
        showDock = scopesExpanded && scopeSettings.visibleTypes().isNotEmpty(),
        modifier = Modifier.background(MonitorBackground),
        preview = {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val previewSize = previewSizeForContainer(
                    containerWidth = with(density) { maxWidth.roundToPx().coerceAtLeast(1) },
                    containerHeight = with(density) { maxHeight.roundToPx().coerceAtLeast(1) },
                    sourceWidth = captureWidth,
                    sourceHeight = captureHeight
                )
                val previewWidth = with(density) { previewSize.width.toDp() }
                val previewHeight = with(density) { previewSize.height.toDp() }
                AndroidView(
                    factory = { ctx ->
                        TextureView(ctx).apply {
                            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                override fun onSurfaceTextureAvailable(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
                                    previewSurfaceReady = true
                                    uvcManager.startPreview(Surface(surface))
                                }

                                override fun onSurfaceTextureSizeChanged(surface: android.graphics.SurfaceTexture, width: Int, height: Int) = Unit
                                override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) = Unit
                                override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean {
                                    previewSurfaceReady = false
                                    uvcManager.stopPreview()
                                    return true
                                }
                            }
                        }
                    },
                    modifier = Modifier.size(previewWidth, previewHeight).align(Alignment.Center)
                )

                if (scopeSettings.safeGuides) SafeGuidesOverlay(Modifier.align(Alignment.Center).size(previewWidth, previewHeight))

                if (!isCameraActive) {
                    ConnectionState(
                        message = statusMessage,
                        onReconnect = {
                            if (!permissionsGranted) requestPermissions()
                            else uvcManager.getUsbDevices().firstOrNull()?.let(uvcManager::requestPermission)
                                ?: run { statusMessage = "No USB capture card detected." }
                        },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                scopeSettings.pinnedScope?.let { pinned ->
                    val overlayWidth = (maxWidth * .38f).coerceIn(160.dp, 240.dp).coerceAtMost(maxWidth)
                    val overlayHeight = (maxHeight * .42f).coerceIn(120.dp, 180.dp).coerceAtMost(maxHeight)
                    ScopePanel(pinned, scopeSettings, if (isCameraActive) scopeFrame else null, changeScopeSettings,
                        Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 20.dp)
                            .width(overlayWidth).height(overlayHeight), overlay = true)
                }
            }
        },
        dock = { modifier ->
            ScopeDock(scopeSettings, if (isCameraActive) scopeFrame else null,
                columns = if (isLandscape) 3 else 2,
                onChange = changeScopeSettings,
                onSettings = { showScopeSettings = true },
                onHide = { scopesExpanded = false }, modifier = modifier)
        },
        topBar = { TopBar(statusMessage, isCameraActive, currentCaptureMode?.label ?: "No signal", isLandscape, Modifier) },
        controls = {
            BottomBar(
                isRecording = isRecording,
                onRecord = { if (isRecording) stopRecording() else startRecording() },
                onSettings = { showSettings = true },
                onScopes = {
                    if (scopeSettings.visibleTypes().isEmpty()) showScopeSettings = true
                    else scopesExpanded = !scopesExpanded
                },
                modifier = Modifier,
                transparent = isLandscape
            )
        }
    )
    if (showScopeSettings) ScopeSettingsDialog(
        scopeSettings, { scopeSettings = it; scopesExpanded = true },
        assistSettings, { assistSettings = it }, { showScopeSettings = false })
    if (showSettings) {
        CaptureSettingsDialog(
            currentCaptureMode = currentCaptureMode,
            captureModes = captureModes,
            recordingSettings = recordingSettings,
            assists = assistSettings,
            onAssistsChange = { assistSettings = it },
            onModeSelected = { mode -> 
                currentCaptureMode = mode
                uvcManager.setPreviewMode(mode)
            },
            onRecordingSettingsChanged = { recordingSettings = it },
            onDismiss = { showSettings = false }
        )
    }
}

@Composable
private fun TopBar(statusMessage: String, isActive: Boolean, signalLabel: String, isLandscape: Boolean, modifier: Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            
            .background(if (isLandscape) Color.Transparent else Color(0xE6080A0D))
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("HAKO", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(" / MONITOR", color = Color(0xFF89939D), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.width(18.dp))
        Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(if (isActive) AccentCyan else Color(0xFF56616B)))
        Spacer(Modifier.width(8.dp))
        Text(statusMessage.uppercase(), color = if (isActive) AccentCyan else Color(0xFFAFB7BE), style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.weight(1f))
        if (isLandscape) {
            Text("SIGNAL  •  $signalLabel", color = Color(0xFFCAD3DA), style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(20.dp))
            Text("INPUT  •  HDMI / UVC", color = Color(0xFF89939D), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ConnectionState(message: String, onReconnect: () -> Unit, modifier: Modifier) {
    Surface(modifier.widthIn(max = 320.dp), color = PanelBackground, shape = RoundedCornerShape(14.dp), tonalElevation = 4.dp) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("NO SIGNAL", color = AccentAmber, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp)); Text(message, color = Color.White, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp)); Button(onClick = onReconnect, colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = Color.Black)) { Text("RECONNECT HDMI") }
        }
    }
}

@Composable
private fun BottomBar(isRecording: Boolean, onRecord: () -> Unit, onSettings: () -> Unit, onScopes: () -> Unit, modifier: Modifier, transparent: Boolean = false) {
    Surface(modifier.fillMaxWidth(), color = if (transparent) Color.Transparent else MonitorBackground) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)) {
            Button(
                onClick = onRecord,
                modifier = Modifier.align(Alignment.Center),
                colors = ButtonDefaults.buttonColors(containerColor = if (isRecording) Color(0xFFE5484D) else Color.White, contentColor = if (isRecording) Color.White else Color.Black),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Icon(if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(if (isRecording) "STOP" else "RECORD")
            }
            Row(Modifier.align(Alignment.CenterEnd)) {
                IconButton(onClick = onScopes) {
                    Icon(Icons.Default.StackedLineChart, contentDescription = "Scopes", tint = AccentCyan, modifier = Modifier.size(26.dp))
                }
                IconButton(onClick = onSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Capture settings", tint = Color.White, modifier = Modifier.size(30.dp))
                }
            }
        }
    }
}

@Composable
private fun SafeGuidesOverlay(modifier: Modifier) {
    Canvas(modifier) { val guide = Color(0x6689A6AF); drawRect(guide, style = Stroke(1f)); drawRect(guide, topLeft = Offset(size.width * .1f, size.height * .1f), size = Size(size.width * .8f, size.height * .8f), style = Stroke(1f)) }
}

@Composable
private fun ScopeSettingsDialog(
    settings: ScopeVisibility,
    onChange: (ScopeVisibility) -> Unit,
    assists: MonitorAssistSettings,
    onAssistsChange: (MonitorAssistSettings) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Monitoring tools") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Use a scope menu to change its channels or pin one translucent scope over the preview.", style = MaterialTheme.typography.bodySmall)
            ScopeType.values().forEach { type -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(type.title, Modifier.weight(1f)); Switch(checked = settings.isVisible(type), onCheckedChange = { onChange(settings.toggle(type)) }) } }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Safe-area guides", Modifier.weight(1f)); Switch(settings.safeGuides, { onChange(settings.copy(safeGuides = it)) }) }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            MonitorAssistControls(assists, onAssistsChange)
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("DONE") } })
}

@Composable
private fun CaptureSettingsDialog(
    currentCaptureMode: CaptureMode?,
    captureModes: List<CaptureMode>,
    recordingSettings: RecordingSettings,
    assists: MonitorAssistSettings,
    onAssistsChange: (MonitorAssistSettings) -> Unit,
    onModeSelected: (CaptureMode) -> Unit,
    onRecordingSettingsChanged: (RecordingSettings) -> Unit,
    onDismiss: () -> Unit
) {
    var modeMenuExpanded by remember { mutableStateOf(false) }
    var codecMenuExpanded by remember { mutableStateOf(false) }
    var resolutionMenuExpanded by remember { mutableStateOf(false) }
    var fpsMenuExpanded by remember { mutableStateOf(false) }
    var bitrateMenuExpanded by remember { mutableStateOf(false) }
    var audioSourceMenuExpanded by remember { mutableStateOf(false) }

    val availableEncoders = remember { getAvailableEncoders() }
    val maxResolution = currentCaptureMode?.let { it.width to it.height }
    val resolutionOptions = remember(maxResolution) { getResolutionOptions(maxResolution) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Capture & Recording") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Section: Input
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("HDMI INPUT", style = MaterialTheme.typography.labelLarge, color = AccentCyan)
                    Text(currentCaptureMode?.let { "Signal: ${it.label}" } ?: "Signal: waiting…", style = MaterialTheme.typography.bodyMedium)
                    Box {
                        OutlinedButton(onClick = { modeMenuExpanded = true }, enabled = captureModes.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                            Text(currentCaptureMode?.label ?: "No modes available")
                        }
                        DropdownMenu(expanded = modeMenuExpanded, onDismissRequest = { modeMenuExpanded = false }) {
                            captureModes.forEach { mode ->
                                DropdownMenuItem(text = { Text(mode.label) }, onClick = { modeMenuExpanded = false; onModeSelected(mode) })
                            }
                        }
                    }
                }

                HorizontalDivider()

                // Section: Recording
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("RECORDING SETTINGS", style = MaterialTheme.typography.labelLarge, color = AccentCyan)

                    // Codec
                    Column {
                        Text("Video Codec", style = MaterialTheme.typography.labelSmall)
                        Box {
                            OutlinedButton(onClick = { codecMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                val currentCodec = availableEncoders.find { it.mimeType == recordingSettings.codec }
                                Text(currentCodec?.friendlyName ?: recordingSettings.codec)
                            }
                            DropdownMenu(expanded = codecMenuExpanded, onDismissRequest = { codecMenuExpanded = false }) {
                                availableEncoders.forEach { codec ->
                                    DropdownMenuItem(
                                        text = { Text("${codec.friendlyName} (${if (codec.isHardwareAccelerated) "HW" else "SW"})") },
                                        onClick = {
                                            codecMenuExpanded = false
                                            onRecordingSettingsChanged(recordingSettings.copy(codec = codec.mimeType))
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Resolution
                    Column {
                        Text("Resolution", style = MaterialTheme.typography.labelSmall)
                        Box {
                            OutlinedButton(onClick = { resolutionMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("${recordingSettings.resolution.first}x${recordingSettings.resolution.second}")
                            }
                            DropdownMenu(expanded = resolutionMenuExpanded, onDismissRequest = { resolutionMenuExpanded = false }) {
                                resolutionOptions.forEach { res ->
                                    DropdownMenuItem(
                                        text = { Text("${res.first}x${res.second}") },
                                        onClick = {
                                            resolutionMenuExpanded = false
                                            onRecordingSettingsChanged(recordingSettings.copy(resolution = res))
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // FPS
                    Column {
                        Text("Frame Rate", style = MaterialTheme.typography.labelSmall)
                        Box {
                            OutlinedButton(onClick = { fpsMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("${recordingSettings.fps} FPS")
                            }
                            DropdownMenu(expanded = fpsMenuExpanded, onDismissRequest = { fpsMenuExpanded = false }) {
                                FpsOptions.forEach { fps ->
                                    DropdownMenuItem(
                                        text = { Text("$fps FPS") },
                                        onClick = {
                                            fpsMenuExpanded = false
                                            onRecordingSettingsChanged(recordingSettings.copy(fps = fps))
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Bitrate
                    Column {
                        Text("Target Bitrate", style = MaterialTheme.typography.labelSmall)
                        Box {
                            OutlinedButton(onClick = { bitrateMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("${recordingSettings.bitrate / 1_000_000} Mbps")
                            }
                            DropdownMenu(expanded = bitrateMenuExpanded, onDismissRequest = { bitrateMenuExpanded = false }) {
                                BitrateOptions.forEach { bitrate ->
                                    DropdownMenuItem(
                                        text = { Text("${bitrate / 1_000_000} Mbps") },
                                        onClick = {
                                            bitrateMenuExpanded = false
                                            onRecordingSettingsChanged(recordingSettings.copy(bitrate = bitrate))
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Column {
                        Text("Audio Source", style = MaterialTheme.typography.labelSmall)
                        Box {
                            OutlinedButton(onClick = { audioSourceMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(recordingSettings.audioSource.label)
                            }
                            DropdownMenu(expanded = audioSourceMenuExpanded, onDismissRequest = { audioSourceMenuExpanded = false }) {
                                RecordingAudioSource.entries.forEach { source ->
                                    DropdownMenuItem(
                                        text = { Text(source.label) },
                                        onClick = {
                                            audioSourceMenuExpanded = false
                                            onRecordingSettingsChanged(recordingSettings.copy(audioSource = source))
                                        }
                                    )
                                }
                            }
                        }
                        Text(
                            recordingSettings.audioSource.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
                HorizontalDivider()
                MonitorAssistControls(assists, onAssistsChange)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("DONE") } }
    )
}

