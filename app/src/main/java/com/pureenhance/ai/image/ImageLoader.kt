package com.pureenhance.ai.image

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.pureenhance.ai.utilities.EnhanceException
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import kotlin.math.roundToInt

/** Bounds-checked, memory-safe decoding. Never decodes more pixels than requested. */
class ImageLoader(private val resolver: ContentResolver) {

    fun readInfo(uri: Uri): ImageInfo {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(uri).use { BitmapFactory.decodeStream(it, null, opts) }
        if (opts.outWidth <= 0 || opts.outHeight <= 0) throw EnhanceException.UnsupportedImage()
        val orientation = runCatching {
            open(uri).use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val swap = swapsAxes(orientation)
        return ImageInfo(
            width = if (swap) opts.outHeight else opts.outWidth,
            height = if (swap) opts.outWidth else opts.outHeight,
            rawWidth = opts.outWidth,
            rawHeight = opts.outHeight,
            exifOrientation = orientation,
            mimeType = opts.outMimeType,
        )
    }

    /** Decodes [uri] so that the oriented result is ~[targetW]×[targetH], using the cheapest sample size. */
    fun decode(uri: Uri, info: ImageInfo, targetW: Int, targetH: Int): Bitmap {
        val swap = swapsAxes(info.exifOrientation)
        val rawTargetW = (if (swap) targetH else targetW).coerceAtLeast(1)
        val rawTargetH = (if (swap) targetW else targetH).coerceAtLeast(1)
        val sample = sampleSizeFor(info.rawWidth, info.rawHeight, rawTargetW, rawTargetH)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = try {
            open(uri).use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (e: OutOfMemoryError) {
            throw EnhanceException.OutOfMemory(e)
        } ?: throw EnhanceException.UnsupportedImage()

        val matrix = Matrix()
        val sx = rawTargetW.toFloat() / decoded.width
        val sy = rawTargetH.toFloat() / decoded.height
        val needScale = kotlin.math.abs(sx - 1f) > 0.002f || kotlin.math.abs(sy - 1f) > 0.002f
        if (needScale) matrix.postScale(sx, sy)
        applyOrientation(matrix, info.exifOrientation)
        if (!needScale && matrix.isIdentity) return decoded
        return try {
            val out = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            if (out !== decoded) decoded.recycle()
            out
        } catch (e: OutOfMemoryError) {
            decoded.recycle()
            throw EnhanceException.OutOfMemory(e)
        }
    }

    private fun open(uri: Uri): InputStream = try {
        resolver.openInputStream(uri) ?: throw EnhanceException.UnsupportedImage()
    } catch (e: FileNotFoundException) {
        throw EnhanceException.UnsupportedImage(e)
    } catch (e: SecurityException) {
        throw EnhanceException.UnsupportedImage(e)
    } catch (e: IOException) {
        throw EnhanceException.UnsupportedImage(e)
    }

    companion object {
        fun swapsAxes(o: Int) = o == ExifInterface.ORIENTATION_ROTATE_90 || o == ExifInterface.ORIENTATION_ROTATE_270 ||
            o == ExifInterface.ORIENTATION_TRANSPOSE || o == ExifInterface.ORIENTATION_TRANSVERSE

        /** Largest power-of-two sample that still yields a bitmap >= the target in both axes. */
        fun sampleSizeFor(rawW: Int, rawH: Int, targetW: Int, targetH: Int): Int {
            var s = 1
            while (rawW / (s * 2) >= targetW && rawH / (s * 2) >= targetH) s *= 2
            return s
        }

        fun applyOrientation(m: Matrix, o: Int) {
            when (o) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(-90f); m.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(-90f)
            }
        }
    }
}
