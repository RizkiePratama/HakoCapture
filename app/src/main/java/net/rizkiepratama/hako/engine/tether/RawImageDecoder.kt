package net.rizkiepratama.hako.engine.tether

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log

object RawImageDecoder {
    private const val TAG = "Hako.RawDecoder"

    fun decodeOrExtractJpeg(rawBytes: ByteArray): Bitmap? {
        if (rawBytes.isEmpty()) return null

        // First attempt direct decoding via BitmapFactory
        try {
            val directBitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
            if (directBitmap != null) {
                return directBitmap
            }
        } catch (e: Exception) {
            Log.w(TAG, "Direct decoding failed: ${e.message}, attempting embedded JPEG extraction")
        }

        // Search for embedded JPEG (SOI: 0xFF 0xD8, EOI: 0xFF 0xD9)
        val jpegBytes = extractLargestJpeg(rawBytes) ?: return null
        return try {
            BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode extracted embedded JPEG", e)
            null
        }
    }

    fun extractLargestJpeg(bytes: ByteArray): ByteArray? {
        if (bytes.size < 4) return null
        val soiIndices = mutableListOf<Int>()
        var i = 0
        val maxScan = bytes.size - 1

        // Find all SOI markers (0xFF 0xD8)
        while (i < maxScan) {
            if ((bytes[i].toInt() and 0xFF) == 0xFF && (bytes[i + 1].toInt() and 0xFF) == 0xD8) {
                soiIndices.add(i)
                // Limit max SOI candidates to prevent high CPU overhead on huge files
                if (soiIndices.size >= 10) break
            }
            i++
        }

        if (soiIndices.isEmpty()) return null

        var bestJpeg: ByteArray? = null
        var maxLen = 0

        for (soi in soiIndices) {
            var j = soi + 2
            var eoi = -1
            while (j < maxScan) {
                // If another SOI starts before finding EOI, stop this stream search
                if ((bytes[j].toInt() and 0xFF) == 0xFF && (bytes[j + 1].toInt() and 0xFF) == 0xD8) {
                    break
                }
                // EOI found
                if ((bytes[j].toInt() and 0xFF) == 0xFF && (bytes[j + 1].toInt() and 0xFF) == 0xD9) {
                    eoi = j + 2
                    break
                }
                j++
            }

            if (eoi > soi) {
                val len = eoi - soi
                if (len > maxLen) {
                    maxLen = len
                    bestJpeg = bytes.copyOfRange(soi, eoi)
                }
            }
        }

        return bestJpeg
    }
}
