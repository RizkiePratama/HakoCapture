package net.rizkiepratama.hako

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import net.rizkiepratama.hako.ui.screens.ModeSelectorScreen
import net.rizkiepratama.hako.ui.screens.MonitorScreen
import net.rizkiepratama.hako.ui.screens.TetherScreen
import net.rizkiepratama.hako.ui.theme.HakoCaptureTheme

enum class AppMode {
    SELECTOR,
    MONITOR,
    TETHER
}

class MainActivity : ComponentActivity() {
    private var permissionsGranted by mutableStateOf(false)
    private var usbAttachVersion by mutableIntStateOf(0)
    private var currentMode by mutableStateOf(AppMode.SELECTOR)

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = results.values.all { it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            show(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.statusBarColor = android.graphics.Color.TRANSPARENT

        checkPermissions()

        setContent {
            HakoCaptureTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val usbManager = remember { getSystemService(Context.USB_SERVICE) as UsbManager }
                    val attachedDevices = remember(usbAttachVersion) {
                        usbManager.deviceList.values.toList()
                    }

                    if (currentMode != AppMode.SELECTOR) {
                        BackHandler {
                            currentMode = AppMode.SELECTOR
                        }
                    }

                    when (currentMode) {
                        AppMode.SELECTOR -> {
                            ModeSelectorScreen(
                                permissionsGranted = permissionsGranted,
                                attachedDevices = attachedDevices,
                                onRequestPermissions = ::checkPermissions,
                                onSelectMonitor = { currentMode = AppMode.MONITOR },
                                onSelectTether = { currentMode = AppMode.TETHER }
                            )
                        }
                        AppMode.MONITOR -> {
                            MonitorScreen(
                                permissionsGranted = permissionsGranted,
                                usbAttachVersion = usbAttachVersion,
                                requestPermissions = ::checkPermissions,
                                onBack = { currentMode = AppMode.SELECTOR }
                            )
                        }
                        AppMode.TETHER -> {
                            TetherScreen(
                                permissionsGranted = permissionsGranted,
                                usbAttachVersion = usbAttachVersion,
                                requestPermissions = ::checkPermissions,
                                onBack = { currentMode = AppMode.SELECTOR }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun checkPermissions() {
        val required = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        permissionsGranted = required.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (!permissionsGranted) requestPermissionsLauncher.launch(required)
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) usbAttachVersion++
    }
}
