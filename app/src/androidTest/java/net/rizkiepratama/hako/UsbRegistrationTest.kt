package net.rizkiepratama.hako

import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.rizkiepratama.hako.engine.UvcCameraManager
import com.serenegiant.usb.UVCCamera
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UsbRegistrationTest {
    @Test
    fun registeringOnModernAndroidActuallyInstallsAReceiver() {
        val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            var installed = false
            var registrationCount = 0
            var removalCount = 0

            override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter, flags: Int): Intent? {
                val result = super.registerReceiver(receiver, filter, flags)
                installed = true
                registrationCount++
                return result
            }

            override fun unregisterReceiver(receiver: BroadcastReceiver) {
                super.unregisterReceiver(receiver)
                removalCount++
            }
        }
        val manager = UvcCameraManager(context, {}, {}, { _, _ -> })
        try {
            manager.register()
            assertTrue("USB events must be registered, not silently canceled after a PendingIntent exception", context.installed)
            manager.register()
            assertEquals("Repeated registration must not leak receivers", 1, context.registrationCount)
        } finally {
            manager.unregister()
        }
        manager.unregister()
        assertEquals("Disposal must release the receiver exactly once", 1, context.removalCount)
    }

    @Test
    fun nativeUvcDriverLoadsOnThisDevice() {
        val camera = UVCCamera()
        camera.destroy()
    }
}
