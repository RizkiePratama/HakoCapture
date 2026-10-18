package net.rizkiepratama.hako.engine.tether

import android.graphics.Bitmap
import java.util.Locale

data class TetheredImage(
    val id: String,
    val objectHandle: Int,
    val filename: String,
    val format: Int,
    val fileSizeBytes: Long,
    val captureTimeMs: Long = System.currentTimeMillis(),
    val bitmap: Bitmap? = null,
    val rawBytes: ByteArray? = null,
    val fileUri: String? = null
) {
    val isRaw: Boolean
        get() = format == PtpConstants.FMT_SONY_RAW || format == PtpConstants.FMT_ARW

    val formatLabel: String
        get() = when (format) {
            PtpConstants.FMT_EXIF_JPEG -> "JPEG"
            PtpConstants.FMT_SONY_RAW, PtpConstants.FMT_ARW -> "ARW / RAW"
            else -> "RAW/IMG"
        }

    val formattedSize: String
        get() {
            val mb = fileSizeBytes / (1024.0 * 1024.0)
            return String.format(Locale.US, "%.2f MB", mb)
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as TetheredImage
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}
