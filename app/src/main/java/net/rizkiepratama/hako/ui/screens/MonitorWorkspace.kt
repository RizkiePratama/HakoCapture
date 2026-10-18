package net.rizkiepratama.hako.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Landscape chrome floats above the preview; portrait chrome occupies its own rows. */
@Composable
internal fun MonitorWorkspace(
    landscape: Boolean,
    showDock: Boolean,
    preview: @Composable () -> Unit,
    dock: @Composable (Modifier) -> Unit,
    topBar: @Composable () -> Unit,
    controls: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    if (landscape) {
        BoxWithConstraints(modifier.fillMaxSize()) {
            val dockWidth = maxWidth * .5f
            Box(Modifier.fillMaxSize().padding(end = if (showDock) dockWidth else 0.dp)) { preview() }
            if (showDock) {
                dock(Modifier.align(Alignment.CenterEnd).width(dockWidth).fillMaxHeight()
                    .safeDrawingPadding().padding(top = 40.dp, bottom = 60.dp))
            }
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))) { topBar() }
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))) { controls() }
        }
    } else {
        Column(modifier.fillMaxSize().safeDrawingPadding()) {
            topBar()
            Column(Modifier.fillMaxWidth().weight(1f)) {
                Box(Modifier.fillMaxWidth().weight(1f)) { preview() }
                if (showDock) dock(Modifier.fillMaxWidth().weight(1f))
            }
            controls()
        }
    }
}
