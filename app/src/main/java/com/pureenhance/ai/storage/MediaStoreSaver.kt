package com.pureenhance.ai.storage

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.pureenhance.ai.utilities.EnhanceException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Saves a *new* file to Pictures/PureEnhance via MediaStore. The original is never touched. No permission needed. */
class MediaStoreSaver(private val context: Context) {

    suspend fun save(bitmap: Bitmap, originalUri: Uri?): Uri = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "PureEnhance_$stamp.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw EnhanceException.StorageFailed()
        try {
            resolver.openOutputStream(uri)?.use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) throw IOException("compress failed")
            } ?: throw IOException("no output stream")
            if (originalUri != null) copyMetadata(originalUri, uri)
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            uri
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw if (t is OutOfMemoryError) EnhanceException.OutOfMemory(t) else EnhanceException.StorageFailed(t)
        }
    }

    /**
     * Best-effort copy of camera/date metadata from the original. GPS location is deliberately NOT copied,
     * so sharing an enhanced photo never leaks where it was taken.
     */
    private fun copyMetadata(from: Uri, to: Uri) {
        runCatching {
            context.contentResolver.openInputStream(from)?.use { input ->
                val src = ExifInterface(input)
                context.contentResolver.openFileDescriptor(to, "rw")?.use { pfd ->
                    val dst = ExifInterface(pfd.fileDescriptor)
                    for (tag in COPIED_TAGS) src.getAttribute(tag)?.let { dst.setAttribute(tag, it) }
                    dst.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                    dst.saveAttributes()
                }
            }
        }
    }

    companion object {
        const val ALBUM = "PureEnhance"
        const val JPEG_QUALITY = 97
        private val COPIED_TAGS = listOf(
            ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_EXPOSURE_TIME, ExifInterface.TAG_FOCAL_LENGTH, ExifInterface.TAG_LENS_MODEL,
        )
    }
}
