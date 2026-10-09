package com.pureenhance.ai.ai.face

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import com.pureenhance.ai.ai.FaceInfo
import com.pureenhance.ai.ai.inference.ModelProvider
import com.pureenhance.ai.ai.inference.OrtModel
import com.pureenhance.ai.ai.models.ModelSpecs
import com.pureenhance.ai.ai.postprocessing.TensorPostprocessor
import com.pureenhance.ai.ai.preprocessing.TensorPreprocessor
import com.pureenhance.ai.utilities.EnhanceException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.max
import kotlin.math.sqrt

/**
 * A restored face crop. Coordinates are in the *input* image space; [strength] already includes the
 * size-based confidence gate (tiny / low-information faces are blended weakly or not at all).
 */
class FaceLayer(
    val cx: Float,
    val cy: Float,
    val side: Float,
    val restored: Bitmap,
    val strength: Float,
)

class FaceRestorer(private val models: ModelProvider) {

    suspend fun restore(
        src: Bitmap,
        faces: List<FaceInfo>,
        baseStrength: Float,
        onProgress: (Float) -> Unit,
    ): List<FaceLayer> {
        val usable = faces.sortedByDescending { it.width * it.height }.take(MAX_FACES)
        if (usable.isEmpty()) return emptyList()
        val model = models.faceModel()
        val out = ArrayList<FaceLayer>()
        for ((i, face) in usable.withIndex()) {
            currentCoroutineContext().ensureActive()
            val gate = gateFor(face.size)
            if (gate > 0f) out += restoreOne(model, src, face, (gate * baseStrength).coerceIn(0f, 1f))
            onProgress((i + 1f) / usable.size)
        }
        return out
    }

    private fun restoreOne(model: OrtModel, src: Bitmap, face: FaceInfo, strength: Float): FaceLayer {
        val n = ModelSpecs.FACE_SIZE
        val side = face.size * CROP_MARGIN
        val left = face.centerX - side / 2f
        val top = face.centerY - side / 2f

        // Crop with edge clamping so faces near the border don't get black fill.
        val crop = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val matrix = Matrix().apply { setScale(n / side, n / side); preTranslate(-left, -top) }
        shader.setLocalMatrix(matrix)
        Canvas(crop).drawRect(0f, 0f, n.toFloat(), n.toFloat(), Paint(Paint.FILTER_BITMAP_FLAG).apply { this.shader = shader })
        val px = IntArray(n * n).also { crop.getPixels(it, 0, n, 0, 0, n, n) }
        crop.recycle()

        val raw = try {
            model.run(TensorPreprocessor.fromPixels(px, n, signed = true), n, n)
        } catch (e: OutOfMemoryError) {
            throw EnhanceException.OutOfMemory(e)
        } catch (e: Exception) {
            throw EnhanceException.InferenceFailed(e)
        }
        if (raw.size != 3 * n * n) throw EnhanceException.InferenceFailed()

        // Identity safety: GFPGAN drifts colour/brightness. Match mean (fully) and contrast (within ±10 %) to the source crop.
        val plane = n * n
        val restored = FloatArray(raw.size) { raw[it] * 0.5f + 0.5f }
        for (c in 0 until 3) {
            val shift = 16 - 8 * c
            val (mi, si) = stats(plane, n) { ((px[it] shr shift) and 255) / 255f }
            val (mo, so) = stats(plane, n) { restored[c * plane + it] }
            val ratio = (si / max(so, 1e-4f)).coerceIn(0.9f, 1.1f)
            for (i in 0 until plane) restored[c * plane + i] = ((restored[c * plane + i] - mo) * ratio + mi).coerceIn(0f, 1f)
        }
        val argb = TensorPostprocessor.cropToArgb(restored, n, 0, 0, n, n, 1, signed = false)
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        bmp.setPixels(argb, 0, n, 0, 0, n, n)
        return FaceLayer(face.centerX, face.centerY, side, bmp, strength)
    }

    /** Mean/std over the central half of the crop (skips hair/background). */
    private inline fun stats(plane: Int, n: Int, value: (Int) -> Float): Pair<Float, Float> {
        var s = 0.0; var sq = 0.0; var cnt = 0
        for (y in n / 4 until 3 * n / 4) for (x in n / 4 until 3 * n / 4) {
            val v = value(y * n + x)
            s += v; sq += v * v; cnt++
        }
        val m = s / cnt
        return m.toFloat() to sqrt(max(sq / cnt - m * m, 0.0)).toFloat()
    }

    companion object {
        const val MAX_FACES = 3
        const val CROP_MARGIN = 1.75f

        /** Faces with too little pixel information are skipped / blended weakly to avoid hallucinated detail. */
        fun gateFor(facePx: Int): Float = when {
            facePx < 40 -> 0f
            facePx < 80 -> 0.5f
            facePx < 140 -> 0.8f
            facePx < 240 -> 0.55f
            else -> 0.3f
        }
    }
}
