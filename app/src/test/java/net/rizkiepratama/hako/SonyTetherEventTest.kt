package net.rizkiepratama.hako

import net.rizkiepratama.hako.engine.tether.PtpConstants
import net.rizkiepratama.hako.engine.tether.SonyTetherEventAction
import net.rizkiepratama.hako.engine.tether.actionForSonyTetherEvent
import net.rizkiepratama.hako.engine.tether.shouldDownloadSonyObject
import org.junit.Assert.assertEquals
import org.junit.Test

class SonyTetherEventTest {
    @Test
    fun propertyChangeParametersAreNotTreatedAsObjectHandles() {
        listOf(0xD21D, 0x5004, 0x5005).forEach { propertyCode ->
            assertEquals(
                SonyTetherEventAction.Ignore,
                actionForSonyTetherEvent(PtpConstants.EC_SONY_PROP_CHANGED, listOf(propertyCode))
            )
        }
    }

    @Test
    fun standardObjectAddedEventDownloadsItsPositiveHandle() {
        assertEquals(
            SonyTetherEventAction.DownloadObject(42),
            actionForSonyTetherEvent(PtpConstants.EC_OBJECT_ADDED, listOf(42))
        )
    }

    @Test
    fun sonyObjectAddedEventDownloadsItsUnsignedSpecialHandle() {
        assertEquals(
            SonyTetherEventAction.DownloadObject(0xFFFFC001.toInt()),
            actionForSonyTetherEvent(PtpConstants.EC_SONY_OBJECT_ADDED2, listOf(-16383))
        )
    }

    @Test
    fun sonyObjectInfoChangedDoesNotRedownloadAnInvalidatedHandle() {
        assertEquals(
            SonyTetherEventAction.Ignore,
            actionForSonyTetherEvent(PtpConstants.EC_SONY_OBJECT_INFO_CHANGED, listOf(-16383))
        )
    }

    @Test
    fun sonyInMemoryHandleCanBeDownloadedForEveryCapture() {
        assertEquals(
            true,
            shouldDownloadSonyObject(0xFFFFC001.toInt(), setOf(0xFFFFC001.toInt()))
        )
    }
}
