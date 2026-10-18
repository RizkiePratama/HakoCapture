package net.rizkiepratama.hako.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import net.rizkiepratama.hako.engine.tether.SonyTetherManager
import net.rizkiepratama.hako.engine.tether.TetheredImage
import net.rizkiepratama.hako.engine.tether.loadSavedTetheredImages
import net.rizkiepratama.hako.engine.tether.deleteSavedTetheredImage
import net.rizkiepratama.hako.engine.tether.loadHighResolutionTetherPreview
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

private val BackgroundColor = Color(0xFF080A0D)
private val PanelBackground = Color(0xD914181E)
private val AccentAmber = Color(0xFFFFC857)
private val AccentCyan = Color(0xFF6DE7E1)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TetherScreen(
    permissionsGranted: Boolean,
    usbAttachVersion: Int,
    onBack: () -> Unit,
    requestPermissions: () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val localView = LocalView.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    var isConnected by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Searching for Sony camera…") }
    var cameraName by remember { mutableStateOf<String?>(null) }

    val downloadedImages = remember { mutableStateListOf<TetheredImage>() }
    var selectedImageId by remember { mutableStateOf<String?>(null) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgressText by remember { mutableStateOf("") }
    var selectionMode by remember { mutableStateOf(false) }
    val selectedImageIds = remember { mutableStateListOf<String>() }
    var confirmDelete by remember { mutableStateOf(false) }

    DisposableEffect(localView, isLandscape) {
        val insetsController = (localView.context as? Activity)?.let { activity ->
            WindowCompat.getInsetsController(activity.window, localView)
        }
        if (isLandscape) {
            insetsController?.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            insetsController?.show(WindowInsetsCompat.Type.statusBars())
        }
        onDispose {
            insetsController?.show(WindowInsetsCompat.Type.statusBars())
        }
    }

    LaunchedEffect(Unit) {
        val history = withContext(Dispatchers.IO) { loadSavedTetheredImages(context) }
        val knownUris = downloadedImages.mapNotNullTo(mutableSetOf()) { it.fileUri }
        downloadedImages.addAll(history.filterNot { it.fileUri in knownUris })
        if (selectedImageId == null) selectedImageId = downloadedImages.firstOrNull()?.id
    }

    val tetherManager = remember {
        SonyTetherManager(
            context = context,
            onConnectionStateChanged = { connected, msg, devName ->
                isConnected = connected
                statusMessage = msg
                cameraName = devName
                if (!connected) {
                    isDownloading = false
                    downloadProgressText = ""
                }
            },
            onNewImageDownloaded = { img ->
                isDownloading = false
                val existingIndex = downloadedImages.indexOfFirst { it.id == img.id }
                if (existingIndex >= 0) {
                    downloadedImages[existingIndex] = img
                } else {
                    downloadedImages.add(0, img)
                }
                selectedImageId = img.id
            },
            onDownloadProgress = { _, downloaded, total ->
                isDownloading = isActiveTetherDownload(downloaded, total)
                if (!isDownloading) {
                    downloadProgressText = ""
                } else {
                    val dMb = downloaded / (1024.0 * 1024.0)
                    val tMb = total / (1024.0 * 1024.0)
                    downloadProgressText = String.format(java.util.Locale.US, "Downloading… %.1f / %.1f MB", dMb, tMb)
                }
            }
        )
    }

    DisposableEffect(Unit) {
        tetherManager.register()
        onDispose {
            tetherManager.unregister()
        }
    }

    LaunchedEffect(permissionsGranted, usbAttachVersion) {
        if (permissionsGranted) {
            val device = tetherManager.getSonyDevices().firstOrNull()
            if (device != null) tetherManager.requestPermission(device)
            else statusMessage = "Searching for Sony camera (USB PC Remote)…"
        } else {
            statusMessage = "USB permissions required for camera tethering."
        }
    }

    val selectedImage = downloadedImages.find { it.id == selectedImageId } ?: downloadedImages.firstOrNull()
    val previewScope = rememberCoroutineScope()
    var previewZoom by remember(selectedImage?.id) { mutableFloatStateOf(1f) }
    var previewOffset by remember(selectedImage?.id) { mutableStateOf(Offset.Zero) }
    var previewViewportSize by remember { mutableStateOf(IntSize.Zero) }
    var highResolutionBitmap by remember(selectedImage?.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var highResolutionLoading by remember(selectedImage?.id) { mutableStateOf(false) }
    var highResolutionAttempted by remember(selectedImage?.id) { mutableStateOf(false) }

    fun requestHighResolutionPreview() {
        val image = selectedImage ?: return
        if (highResolutionAttempted || image.fileUri == null) return
        highResolutionAttempted = true
        highResolutionLoading = true
        previewScope.launch {
            highResolutionBitmap = withContext(Dispatchers.IO) {
                loadHighResolutionTetherPreview(context, image)
            }
            highResolutionLoading = false
        }
    }

    fun actionImages(): List<TetheredImage> {
        val ids = tetherActionImageIds(selectionMode, selectedImage?.id, selectedImageIds.toSet())
        return ids.mapNotNull { id -> downloadedImages.find { it.id == id } }
    }

    fun shareImages() {
        val uris = ArrayList(actionImages().mapNotNull { it.fileUri?.let(Uri::parse) })
        if (uris.isEmpty()) return
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uris.first()) }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply { putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris) }
        }.apply {
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share tethered image${if (uris.size > 1) "s" else ""}"))
    }

    fun deleteImages() {
        val targets = actionImages()
        targets.forEach { image ->
            runCatching { deleteSavedTetheredImage(context, image) }
            downloadedImages.remove(image)
        }
        selectedImageIds.clear()
        selectionMode = false
        selectedImageId = downloadedImages.firstOrNull()?.id
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        val useCompactLandscapeLayout = useLandscapeTetherLayout(maxWidth.value.toInt(), maxHeight.value.toInt())
        Column(modifier = Modifier.fillMaxSize()) {
        // A single compact command/status bar leaves maximum room for the image preview.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xF20B0E12))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }

            Text("HAKO", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(" / TETHER", color = AccentAmber, style = MaterialTheme.typography.labelMedium)

            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(7.dp).clip(CircleShape).background(if (isConnected) AccentCyan else Color(0xFF56616B)))
            Spacer(Modifier.width(6.dp))
            Text(
                text = (if (isConnected) cameraName ?: statusMessage else statusMessage).uppercase(),
                color = if (isConnected) AccentCyan else Color(0xFFAFB7BE),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (downloadedImages.isNotEmpty()) {
                if (selectionMode) {
                    Text("${selectedImageIds.size} selected", color = AccentAmber, style = MaterialTheme.typography.labelMedium)
                    IconButton(onClick = ::shareImages, enabled = selectedImageIds.isNotEmpty()) {
                        Icon(Icons.Default.Share, contentDescription = "Share selected", tint = Color.White)
                    }
                    IconButton(onClick = { confirmDelete = true }, enabled = selectedImageIds.isNotEmpty()) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete selected", tint = Color(0xFFFF7D7D))
                    }
                    IconButton(onClick = { selectionMode = false; selectedImageIds.clear() }) {
                        Icon(Icons.Default.Close, contentDescription = "Exit selection", tint = Color.White)
                    }
                } else {
                    IconButton(onClick = { selectionMode = true; selectedImage?.id?.let(selectedImageIds::add) }) {
                        Icon(Icons.Default.SelectAll, contentDescription = "Select images", tint = Color(0xFFAFB7BE))
                    }
                    IconButton(onClick = ::shareImages) {
                        Icon(Icons.Default.Share, contentDescription = "Share image", tint = Color(0xFFAFB7BE))
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete image", tint = Color(0xFFFF9A9A))
                    }
                }
            }

            FilledIconButton(
                onClick = {
                    val device = tetherManager.getSonyDevices().firstOrNull()
                    when (tetherPrimaryAction(permissionsGranted, isConnected, device != null)) {
                        TetherPrimaryAction.REQUEST_PERMISSIONS -> requestPermissions()
                        TetherPrimaryAction.CONNECT -> device?.let(tetherManager::requestPermission)
                        TetherPrimaryAction.SYNC -> tetherManager.manualSync()
                        TetherPrimaryAction.UNAVAILABLE -> {
                            statusMessage = "Searching for Sony camera (USB PC Remote)…"
                        }
                    }
                },
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = AccentAmber, contentColor = Color.Black)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Manual sync", modifier = Modifier.size(18.dp))
            }
        }

        // Main Content Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = if (useCompactLandscapeLayout) 12.dp else 8.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF050608))
                .border(1.dp, Color(0xFF202730), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            val previewImage = selectedImage
            val displayedBitmap = highResolutionBitmap ?: previewImage?.bitmap
            if (displayedBitmap != null && previewImage != null) {
                LaunchedEffect(
                    displayedBitmap.width,
                    displayedBitmap.height,
                    previewViewportSize,
                    previewZoom
                ) {
                    val (x, y) = clampTetherPan(
                        previewOffset.x,
                        previewOffset.y,
                        previewZoom,
                        previewViewportSize.width,
                        previewViewportSize.height,
                        displayedBitmap.width,
                        displayedBitmap.height
                    )
                    previewOffset = Offset(x, y)
                }
                Image(
                    bitmap = displayedBitmap.asImageBitmap(),
                    contentDescription = previewImage.filename,
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { previewViewportSize = it }
                        .pointerInput(previewImage.id) {
                            detectTapGestures(
                                onDoubleTap = {
                                    previewZoom = tetherZoomAfterDoubleTap(previewZoom)
                                    if (previewZoom == 1f) previewOffset = Offset.Zero
                                    else requestHighResolutionPreview()
                                }
                            )
                        }
                        .pointerInput(previewImage.id) {
                            detectTransformGestures { _, pan, gestureZoom, _ ->
                                val nextZoom = clampTetherZoom(previewZoom * gestureZoom)
                                previewZoom = nextZoom
                                val candidate = if (nextZoom == 1f) Offset.Zero else previewOffset + pan
                                val (x, y) = clampTetherPan(
                                    candidate.x,
                                    candidate.y,
                                    nextZoom,
                                    previewViewportSize.width,
                                    previewViewportSize.height,
                                    displayedBitmap.width,
                                    displayedBitmap.height
                                )
                                previewOffset = Offset(x, y)
                                if (nextZoom > 1f) requestHighResolutionPreview()
                            }
                        }
                        .graphicsLayer {
                            scaleX = previewZoom
                            scaleY = previewZoom
                            translationX = previewOffset.x
                            translationY = previewOffset.y
                        },
                    contentScale = ContentScale.Fit
                )

                // Metadata HUD Overlay
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    color = PanelBackground,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(previewImage.filename, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = AccentAmber.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                                Text(
                                    previewImage.formatLabel,
                                    color = AccentAmber,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(previewImage.formattedSize, color = Color(0xFFCAD3DA), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                if (highResolutionLoading) {
                    Surface(
                        modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                        color = PanelBackground,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = AccentCyan, strokeWidth = 2.dp)
                            Text("LOADING HIGH-RES PREVIEW", color = Color.White, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            } else if (!isConnected) {
                Surface(
                    modifier = Modifier.widthIn(max = 360.dp),
                    color = PanelBackground,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("SONY TETHER DISCONNECTED", color = AccentAmber, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(8.dp))
                        Text(statusMessage, color = Color.White, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                if (!permissionsGranted) requestPermissions()
                                else tetherManager.getSonyDevices().firstOrNull()?.let(tetherManager::requestPermission)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentAmber, contentColor = Color.Black)
                        ) {
                            Text("CONNECT CAMERA")
                        }
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("CAMERA READY", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("Fire shutter on Sony camera to pull new image", color = Color(0xFF89939D), style = MaterialTheme.typography.bodyMedium)
                }
            }

            // Downloading Progress Overlay
            if (isDownloading) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = if (useCompactLandscapeLayout) 12.dp else 24.dp),
                    color = PanelBackground,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = AccentAmber, strokeWidth = 2.dp)
                        Text(downloadProgressText, color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // Bottom Thumbnail Strip
        if (downloadedImages.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF0B0E12)
            ) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp, horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(downloadedImages, key = { it.id }) { img ->
                        val isSelected = img.id == selectedImage?.id
                        val isMultiSelected = img.id in selectedImageIds
                        Surface(
                            modifier = Modifier
                                .size(if (useCompactLandscapeLayout) 60.dp else 72.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    width = if (isSelected || isMultiSelected) 2.dp else 1.dp,
                                    color = if (isMultiSelected) AccentCyan else if (isSelected) AccentAmber else Color(0xFF262D37),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    selectedImageId = img.id
                                    if (selectionMode) {
                                        if (img.id in selectedImageIds) selectedImageIds.remove(img.id)
                                        else selectedImageIds.add(img.id)
                                    }
                                },
                            color = PanelBackground
                        ) {
                            Box(Modifier.fillMaxSize()) {
                                if (img.bitmap != null) {
                                    Image(
                                        bitmap = img.bitmap.asImageBitmap(),
                                        contentDescription = img.filename,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(img.formatLabel, color = Color.White, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                if (isMultiSelected) {
                                    Surface(
                                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                                        shape = CircleShape,
                                        color = AccentCyan
                                    ) {
                                        Text("✓", color = Color.Black, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }

    if (confirmDelete) {
        val count = actionImages().size
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${if (count == 1) "image" else "$count images"}?") },
            text = { Text("This removes ${if (count == 1) "the saved file" else "the saved files"} from this device.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; deleteImages() }) {
                    Text("DELETE", color = Color(0xFFFF7D7D))
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("CANCEL") } },
            containerColor = Color(0xFF151A20)
        )
    }
}
