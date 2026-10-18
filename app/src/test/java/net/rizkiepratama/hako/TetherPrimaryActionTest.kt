package net.rizkiepratama.hako

import net.rizkiepratama.hako.ui.screens.TetherPrimaryAction
import net.rizkiepratama.hako.ui.screens.tetherPrimaryAction
import org.junit.Assert.assertEquals
import org.junit.Test

class TetherPrimaryActionTest {
    @Test
    fun connectedCameraUsesManualSync() {
        assertEquals(
            TetherPrimaryAction.SYNC,
            tetherPrimaryAction(permissionsGranted = true, isConnected = true, hasDevice = true)
        )
    }

    @Test
    fun disconnectedCameraRequestsUsbConnection() {
        assertEquals(
            TetherPrimaryAction.CONNECT,
            tetherPrimaryAction(permissionsGranted = true, isConnected = false, hasDevice = true)
        )
    }
}
