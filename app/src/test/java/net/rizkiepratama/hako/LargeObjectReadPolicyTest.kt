package net.rizkiepratama.hako

import net.rizkiepratama.hako.engine.tether.isCompleteLargeObjectRead
import net.rizkiepratama.hako.engine.tether.shouldRetryLargeObjectRead
import net.rizkiepratama.hako.engine.tether.usbObjectReadBufferSize
import net.rizkiepratama.hako.engine.tether.smallerUsbObjectReadSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LargeObjectReadPolicyTest {
    @Test
    fun transientUsbReadFailuresAreRetried() {
        assertTrue(shouldRetryLargeObjectRead(1))
        assertTrue(shouldRetryLargeObjectRead(3))
        assertFalse(shouldRetryLargeObjectRead(4))
    }

    @Test
    fun partialObjectIsNeverReportedAsComplete() {
        assertFalse(isCompleteLargeObjectRead(4_784_116, 85_632_512))
        assertTrue(isCompleteLargeObjectRead(85_632_512, 85_632_512))
    }

    @Test
    fun modernAndroidReadsRawObjectsInMultiMegabyteChunks() {
        assertEquals(4 * 1024 * 1024, usbObjectReadBufferSize(androidApiLevel = 35))
        assertEquals(16 * 1024, usbObjectReadBufferSize(androidApiLevel = 27))
    }

    @Test
    fun rejectedUsbReadSizesStepDownWithoutAbandoningTheTransfer() {
        assertEquals(1024 * 1024, smallerUsbObjectReadSize(4 * 1024 * 1024))
        assertEquals(256 * 1024, smallerUsbObjectReadSize(1024 * 1024))
        assertEquals(64 * 1024, smallerUsbObjectReadSize(256 * 1024))
        assertEquals(16 * 1024, smallerUsbObjectReadSize(64 * 1024))
        assertEquals(null, smallerUsbObjectReadSize(16 * 1024))
    }
}
