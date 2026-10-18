package net.rizkiepratama.hako.engine.tether

/**
 * Finds Sony D215 (ObjectInMemory) in the concatenated 0x9209 descriptor blob.
 * D215 is UINT16; Sony descriptors contain code, type, flags, default, current.
 */
fun findSonyObjectInMemoryValue(payload: ByteArray): Int? {
    for (offset in 0..payload.size - SONY_UINT16_DESCRIPTOR_MIN_SIZE) {
        val isObjectInMemory =
            (payload[offset].toInt() and 0xFF) == 0x15 &&
                (payload[offset + 1].toInt() and 0xFF) == 0xD2
        val isUint16 =
            (payload[offset + 2].toInt() and 0xFF) == 0x04 &&
                payload[offset + 3].toInt() == 0
        if (isObjectInMemory && isUint16) {
            return (payload[offset + 8].toInt() and 0xFF) or
                ((payload[offset + 9].toInt() and 0xFF) shl 8)
        }
    }
    return null
}

private const val SONY_UINT16_DESCRIPTOR_MIN_SIZE = 10
