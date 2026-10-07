package com.pureenhance.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pureenhance.ai.ai.ImageAnalyzer
import com.pureenhance.ai.ai.Exposure
import com.pureenhance.ai.ai.FaceInfo
import com.pureenhance.ai.ai.Level
import com.pureenhance.ai.ai.face.FaceDetecting
import com.pureenhance.ai.ai.inference.ModelStore
import com.pureenhance.ai.domain.EditParams
import com.pureenhance.ai.image.BitmapUtils
import com.pureenhance.ai.image.ImageLoader
import com.pureenhance.ai.storage.MediaStoreSaver
import com.pureenhance.ai.utilities.EnhanceException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Random

@RunWith(AndroidJUnit4::class)
class ImageAndStorageInstrumentedTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val loader = ImageLoader(ctx.contentResolver)
    private val noFaces = object : FaceDetecting { override suspend fun detect(bitmap: Bitmap) = emptyList<FaceInfo>() }

    private fun writeJpeg(name: String, bmp: Bitmap, orientation: Int? = null): Uri {
        val f = File(ctx.cacheDir, name)
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        if (orientation != null) ExifInterface(f.absolutePath).apply { setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString()); saveAttributes() }
        return Uri.fromFile(f)
    }

    private fun solid(w: Int, h: Int, c: Int) = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(c) }

    @Test fun loadsReadsBoundsAndDownsamples() {
        val uri = writeJpeg("big.jpg", solid(3000, 2000, Color.GRAY))
        val info = loader.readInfo(uri)
        assertEquals(3000, info.width); assertEquals(2000, info.height)
        val small = loader.decode(uri, info, 750, 500)
        assertTrue(small.width in 749..751 && small.height in 499..501)
    }

    @Test fun appliesExifRotation() {
        val uri = writeJpeg("rot.jpg", solid(400, 200, Color.RED), ExifInterface.ORIENTATION_ROTATE_90)
        val info = loader.readInfo(uri)
        assertEquals(200, info.width); assertEquals(400, info.height)
        val bmp = loader.decode(uri, info, 200, 400)
        assertEquals(200, bmp.width); assertEquals(400, bmp.height)
    }

    @Test fun corruptedFileGivesFriendlyError() {
        val f = File(ctx.cacheDir, "bad.jpg").apply { writeBytes(ByteArray(64) { it.toByte() }) }
        try { loader.readInfo(Uri.fromFile(f)); fail() } catch (e: EnhanceException.UnsupportedImage) { assertTrue(e.userMessage.isNotBlank()) }
    }

    @Test fun resizeKeepsRequestedSize() {
        val out = BitmapUtils.resize(solid(2000, 1000, Color.BLUE), 250, 125)
        assertEquals(250, out.width); assertEquals(125, out.height)
    }

    @Test fun missingModelReportsModelMissing() {
        try { ModelStore(ctx).resolve("does_not_exist.onnx"); fail() } catch (e: EnhanceException.ModelMissing) { assertTrue(e.userMessage.contains("does_not_exist")) }
    }

    @Test fun analyzerFlagsDarkNoisyLowResImage() = runBlocking {
        val r = Random(3)
        val dark = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888)
        val px = IntArray(320 * 240) { val v = (20 + r.nextGaussian() * 14).toInt().coerceIn(0, 255); Color.rgb(v, v, v) }
        dark.setPixels(px, 0, 320, 0, 0, 320, 240)
        val p = ImageAnalyzer(noFaces).analyze(dark)
        assertEquals(Exposure.DARK, p.exposure); assertEquals(Level.LOW, p.resolution)
        assertTrue(p.noise != Level.LOW); assertEquals(4, p.recommendedScale); assertTrue(p.faces.isEmpty())
    }

    @Test fun analyzerSeesLandscapeLargeAndFaces() = runBlocking {
        val big = solid(2400, 1600, Color.rgb(120, 140, 160))
        val faces = object : FaceDetecting { override suspend fun detect(bitmap: Bitmap) = listOf(FaceInfo(10, 10, 300, 300), FaceInfo(400, 50, 600, 300)) }
        val p = ImageAnalyzer(faces).analyze(big)
        assertEquals(Level.HIGH, p.resolution); assertEquals(2, p.faces.size); assertEquals(2, p.recommendedScale)
    }

    @Test fun saveCreatesNewFileInPureEnhanceAlbumAndLeavesOriginalAlone() = runBlocking {
        val original = writeJpeg("orig.jpg", solid(64, 64, Color.GREEN))
        val before = File(original.path!!).length()
        val saved = MediaStoreSaver(ctx).save(solid(128, 128, Color.MAGENTA), original)
        ctx.contentResolver.query(saved, arrayOf(MediaStore.Images.Media.RELATIVE_PATH, MediaStore.Images.Media.IS_PENDING), null, null, null)!!.use {
            assertTrue(it.moveToFirst())
            assertTrue(it.getString(0).startsWith("${Environment.DIRECTORY_PICTURES}/PureEnhance"))
            assertEquals(0, it.getInt(1))
        }
        assertEquals(before, File(original.path!!).length())
        assertNotNull(ctx.contentResolver.openInputStream(saved)); ctx.contentResolver.delete(saved, null, null)
        Unit
    }

    @Test fun editParamsDefaultsAreStable() {
        assertEquals(EditParams(), EditParams())
        assertTrue(EditParams.Original != EditParams())
    }
}
