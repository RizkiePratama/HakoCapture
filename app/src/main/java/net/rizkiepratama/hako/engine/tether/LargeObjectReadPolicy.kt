package net.rizkiepratama.hako.engine.tether

private const val MAX_CONSECUTIVE_USB_READ_FAILURES = 3
private const val LEGACY_USB_TRANSFER_LIMIT = 16 * 1024
private const val FAST_USB_OBJECT_CHUNK = 4 * 1024 * 1024

fun usbObjectReadBufferSize(androidApiLevel: Int): Int =
    if (androidApiLevel >= 28) FAST_USB_OBJECT_CHUNK else LEGACY_USB_TRANSFER_LIMIT

fun smallerUsbObjectReadSize(currentSize: Int): Int? = when {
    currentSize > 1024 * 1024 -> 1024 * 1024
    currentSize > 256 * 1024 -> 256 * 1024
    currentSize > 64 * 1024 -> 64 * 1024
    currentSize > LEGACY_USB_TRANSFER_LIMIT -> LEGACY_USB_TRANSFER_LIMIT
    else -> null
}

fun shouldRetryLargeObjectRead(consecutiveFailures: Int): Boolean =
    consecutiveFailures <= MAX_CONSECUTIVE_USB_READ_FAILURES

fun isCompleteLargeObjectRead(downloadedBytes: Long, targetBytes: Long): Boolean =
    targetBytes > 0 && downloadedBytes >= targetBytes
