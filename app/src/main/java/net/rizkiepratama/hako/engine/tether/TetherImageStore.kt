package net.rizkiepratama.hako.engine.tether

import android.content.ContentValues
import android.content.Context
import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.util.Size
import java.io.File
import java.io.IOException

private const val TETHER_PREVIEW_DIRECTORY = "tether_previews"

fun tetherThumbnailFilename(filename: String): String = "$filename.preview.jpg"

private fun tetherThumbnailFile(context: Context, filename: String): File =
    File(File(context.filesDir, TETHER_PREVIEW_DIRECTORY), tetherThumbnailFilename(filename))

fun saveTetherThumbnail(context: Context, filename: String, bitmap: Bitmap?) {
    if (bitmap == null) return
    runCatching {
        val file = tetherThumbnailFile(context, filename)
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
    }
}

fun deleteSavedTetheredImage(context: Context, image: TetheredImage) {
    image.fileUri?.let { context.contentResolver.delete(Uri.parse(it), null, null) }
    tetherThumbnailFile(context, image.filename).delete()
}

fun loadHighResolutionTetherPreview(context: Context, image: TetheredImage): Bitmap? {
    val uri = image.fileUri?.let(Uri::parse) ?: return image.bitmap
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return image.bitmap
    return RawImageDecoder.decodeOrExtractJpeg(bytes) ?: image.bitmap
}

fun storedTetherFormat(mimeType: String?, filename: String): Int =
    if (mimeType == "image/x-sony-arw" || filename.endsWith(".ARW", ignoreCase = true)) {
        PtpConstants.FMT_ARW
    } else {
        PtpConstants.FMT_EXIF_JPEG
    }

fun storedTetherTimestampMs(dateAddedSeconds: Long): Long = dateAddedSeconds * 1_000L

fun loadSavedTetheredImages(context: Context): List<TetheredImage> {
    val resolver = context.contentResolver
    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.MIME_TYPE,
        MediaStore.Images.Media.SIZE,
        MediaStore.Images.Media.DATE_ADDED
    )
    val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? AND ${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
    val selectionArgs = arrayOf("Pictures/HakoCapture/%", "HAKO_%")

    return runCatching {
        resolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            buildList {
                while (cursor.moveToNext()) {
                    val mediaId = cursor.getLong(idColumn)
                    val uri = ContentUris.withAppendedId(collection, mediaId)
                    val filename = cursor.getString(nameColumn)
                    val bitmap = BitmapFactory.decodeFile(tetherThumbnailFile(context, filename).absolutePath)
                        ?: runCatching { resolver.loadThumbnail(uri, Size(1024, 1024), null) }.getOrNull()
                    add(
                        TetheredImage(
                            id = uri.toString(),
                            objectHandle = 0,
                            filename = filename,
                            format = storedTetherFormat(cursor.getString(mimeColumn), filename),
                            fileSizeBytes = cursor.getLong(sizeColumn),
                            captureTimeMs = storedTetherTimestampMs(cursor.getLong(dateColumn)),
                            bitmap = bitmap,
                            fileUri = uri.toString()
                        )
                    )
                }
            }
        }.orEmpty()
    }.getOrDefault(emptyList())
}

fun tetherFilename(handle: Int, sequence: Long, format: Int): String {
    val extension = if (format == PtpConstants.FMT_SONY_RAW || format == PtpConstants.FMT_ARW) "ARW" else "JPG"
    return "HAKO_${handle.toUInt()}_$sequence.$extension"
}

fun saveTetheredImage(context: Context, filename: String, format: Int, bytes: ByteArray): String {
    val mimeType = if (format == PtpConstants.FMT_SONY_RAW || format == PtpConstants.FMT_ARW) {
        "image/x-sony-arw"
    } else {
        "image/jpeg"
    }
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
        put(MediaStore.Images.Media.MIME_TYPE, mimeType)
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/HakoCapture")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: throw IOException("Unable to create MediaStore entry")
    try {
        resolver.openOutputStream(uri)?.use { it.write(bytes) }
            ?: throw IOException("Unable to open MediaStore output")
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri.toString()
    } catch (error: Exception) {
        resolver.delete(uri, null, null)
        throw error
    }
}
