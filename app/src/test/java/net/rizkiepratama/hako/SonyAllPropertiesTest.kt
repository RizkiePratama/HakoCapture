package net.rizkiepratama.hako

import net.rizkiepratama.hako.engine.tether.findSonyObjectInMemoryValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SonyAllPropertiesTest {
    @Test
    fun findsObjectInMemoryCurrentValueInsideAllPropertyDescriptors() {
        val payload = byteArrayOf(
            0x04, 0x50, 0x02, 0x00, 0x00, 0x01, 0x00, 0x00,
            0x15, 0xD2.toByte(), 0x04, 0x00, 0x00, 0x01,
            0x00, 0x00, 0x02, 0x80.toByte(), 0x00,
            0x1D, 0xD2.toByte(), 0x02, 0x00
        )

        assertEquals(0x8002, findSonyObjectInMemoryValue(payload))
    }

    @Test
    fun ignoresBytePatternThatIsNotAUint16D215Descriptor() {
        val payload = byteArrayOf(0x15, 0xD2.toByte(), 0x02, 0x00, 0, 1, 0, 0, 1, 0)
        assertNull(findSonyObjectInMemoryValue(payload))
    }
}
