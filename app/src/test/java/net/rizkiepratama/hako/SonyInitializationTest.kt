package net.rizkiepratama.hako

import net.rizkiepratama.hako.engine.tether.PtpConstants
import net.rizkiepratama.hako.engine.tether.sonyInitializationSteps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SonyInitializationTest {
    @Test
    fun initializationReadsDeviceInfoBeforeOpeningSession() {
        val steps = sonyInitializationSteps()

        assertEquals(PtpConstants.OC_GET_DEVICE_INFO, steps[0].opcode)
        assertTrue(steps[0].expectsData)
        assertEquals(PtpConstants.OC_OPEN_SESSION, steps[1].opcode)
        assertEquals(listOf(1), steps[1].params)
    }

    @Test
    fun sonySdioHandshakeUsesConnectPhasesAndExtendedInfoQuery() {
        val steps = sonyInitializationSteps().drop(2)

        assertEquals(
            listOf(
                PtpConstants.OC_SONY_SDIO_CONNECT,
                PtpConstants.OC_SONY_SDIO_CONNECT,
                PtpConstants.OC_SONY_SDIO_GET_EXT_DEVICE_INFO,
                PtpConstants.OC_SONY_SDIO_CONNECT
            ),
            steps.map { it.opcode }
        )
        assertEquals(listOf(1, 0, 0), steps[0].params)
        assertEquals(listOf(2, 0, 0), steps[1].params)
        assertEquals(listOf(0xC8), steps[2].params)
        assertEquals(listOf(3, 0, 0), steps[3].params)
        assertTrue(steps.all { it.expectsData })
    }
}
