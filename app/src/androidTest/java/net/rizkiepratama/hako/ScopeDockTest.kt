package net.rizkiepratama.hako

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.*
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import net.rizkiepratama.hako.engine.analyzeRgbaFrame
import net.rizkiepratama.hako.ui.screens.ScopeDock
import net.rizkiepratama.hako.ui.screens.ScopeVisibility
import net.rizkiepratama.hako.ui.screens.MonitorWorkspace
import net.rizkiepratama.hako.ui.theme.HakoCaptureTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import java.io.File
import kotlin.math.sin
import kotlin.random.Random

class ScopeDockTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun landscapePreviewExtendsUnderBothBars() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                MonitorWorkspace(landscape = true, showDock = false,
                    preview = { Box(Modifier.fillMaxSize().testTag("preview")) },
                    dock = {},
                    topBar = { Text("Top", Modifier.height(40.dp)) },
                    controls = { Text("Controls", Modifier.height(60.dp)) },
                    modifier = Modifier.testTag("workspace"))
            } }
            val workspace = compose.onNodeWithTag("workspace").fetchSemanticsNode().boundsInRoot
            val preview = compose.onNodeWithTag("preview").fetchSemanticsNode().boundsInRoot
            assertEquals(workspace.height, preview.height, .5f)
            assertEquals(workspace.top, preview.top, .5f)
        }
    }

    @Test fun pinningLeavesOneOverlayWhenTheDockCloses() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onAllNodesWithContentDescription("Channel layout")[1].performClick()
            compose.onNodeWithText("Pin over preview").performClick()
            compose.onNodeWithContentDescription("Hide scopes").assertDoesNotExist()
            compose.onNodeWithContentDescription("Unpin scope").assertIsDisplayed()
            compose.onAllNodesWithText("RGB Waveform").assertCountEquals(1)
            compose.onNodeWithContentDescription("Unpin scope").performClick()
            compose.onNodeWithText("RGB Waveform").assertDoesNotExist()
        }
    }

    @Test fun monitorScopesCanBeCollapsedAndReopened() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText("RGB Waveform").assertIsDisplayed()
            compose.onNodeWithContentDescription("Hide scopes").performClick()
            compose.onNodeWithText("RGB Waveform").assertDoesNotExist()
            compose.onNodeWithContentDescription("Scopes").performClick()
            compose.onNodeWithText("RGB Waveform").assertIsDisplayed()
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            File(instrumentation.targetContext.getExternalFilesDir(null), "monitor-dock-preview.png").outputStream().use { output ->
                screenshot.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            screenshot.recycle()
        }
    }

    @Test fun channelMenusSwitchLayoutsAndDockCanBeHidden() {
        val random = Random(42)
        val rgba = ByteArray(320 * 180 * 4)
        for (y in 0 until 180) for (x in 0 until 320) {
            val base = 95 + 55 * sin(x / 53.0) + 35 * sin(x / 19.0)
            val spread = (y - 90) * (.15 + .4 * sin(x / 80.0) * sin(x / 80.0))
            for (channel in 0..2) {
                rgba[(y * 320 + x) * 4 + channel] = (base + spread + 22 * sin(x / 42.0 + channel) + random.nextDouble(-5.0, 5.0)).toInt().coerceIn(0, 255).toByte()
            }
            rgba[(y * 320 + x) * 4 + 3] = -1
        }
        val frame = analyzeRgbaFrame(rgba, 320, 180)
        var hidden = false
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    HakoCaptureTheme {
                        var settings by remember { mutableStateOf(ScopeVisibility()) }
                        ScopeDock(settings, frame, columns = 2, onChange = { settings = it }, onSettings = {}, onHide = { hidden = true }, modifier = Modifier.fillMaxSize().safeDrawingPadding())
                    }
                }
            }
            compose.onNodeWithText("RGB Waveform").assertIsDisplayed()
            compose.onAllNodesWithContentDescription("Channel layout")[1].performClick()
            compose.onNodeWithText("Split").performClick()
            compose.onNodeWithText("RGB Parade").assertIsDisplayed()
            compose.onAllNodesWithContentDescription("Channel layout")[2].performClick()
            compose.onNodeWithText("Combined").performClick()
            compose.waitForIdle()
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            File(instrumentation.targetContext.getExternalFilesDir(null), "scope-density-preview.png").outputStream().use {
                screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            screenshot.recycle()
            compose.onNodeWithContentDescription("Hide scopes").performClick()
            compose.runOnIdle { assertTrue(hidden) }
        }
    }
}
