package com.pureenhance.ai.ai

import android.graphics.Bitmap
import com.pureenhance.ai.ai.face.FaceDetecting
import com.pureenhance.ai.image.BitmapUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Looks at the photo once and builds the [EnhancementProfile] that drives the pipeline. */
class ImageAnalyzer(private val faceDetector: FaceDetecting) {

    suspend fun analyze(bmp: Bitmap): EnhancementProfile = withContext(Dispatchers.Default) {
        val small = BitmapUtils.fitWithin(bmp, 1024)
        val sw = small.width
        val sh = small.height
        val spx = IntArray(sw * sh).also { small.getPixels(it, 0, sw, 0, 0, sw, sh) }
        val lum = ImageMetrics.luminance(spx)
        val hist = ImageMetrics.histogram(lum)

        val mean = ImageMetrics.mean(lum)
        val range = ImageMetrics.percentile(hist, 0.99) - ImageMetrics.percentile(hist, 0.01)
        val sat = ImageMetrics.meanSaturation(spx)
        val lap = ImageMetrics.laplacianVariance(lum, sw, sh)

        // Noise and JPEG blocking are measured at native resolution on a centre crop aligned to the 8×8 grid.
        val cw = minOf(bmp.width, 512) / 8 * 8
        val ch = minOf(bmp.height, 512) / 8 * 8
        var noiseSigma = 0f
        var block = 1f
        if (cw >= 16 && ch >= 16) {
            val cx = (bmp.width - cw) / 2 / 8 * 8
            val cy = (bmp.height - ch) / 2 / 8 * 8
            val cpx = IntArray(cw * ch).also { bmp.getPixels(it, 0, cw, cx, cy, cw, ch) }
            val cl = ImageMetrics.luminance(cpx)
            noiseSigma = ImageMetrics.noiseSigma(cl, cw, ch)
            block = ImageMetrics.blockiness(cl, cw, ch)
        }

        val k = bmp.width.toFloat() / sw
        val faces = try {
            faceDetector.detect(small).map {
                FaceInfo((it.left * k).roundToInt(), (it.top * k).roundToInt(), (it.right * k).roundToInt(), (it.bottom * k).roundToInt())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList() // face detection is optional; never fail the whole job because of it
        }
        if (small !== bmp) small.recycle()

        val longSide = maxOf(bmp.width, bmp.height)
        val resolution = when { longSide < 800 -> Level.LOW; longSide < 1800 -> Level.MEDIUM; else -> Level.HIGH }
        val noise = when { noiseSigma > 7f -> Level.HIGH; noiseSigma > 3.5f -> Level.MEDIUM; else -> Level.LOW }
        val blur = when { lap < 40f -> Level.HIGH; lap < 120f -> Level.MEDIUM; else -> Level.LOW }
        val exposure = when { mean < 0.28f -> Exposure.DARK; mean > 0.72f -> Exposure.BRIGHT; else -> Exposure.NORMAL }
        val contrast = when { range < 0.50f -> Level.LOW; range < 0.75f -> Level.MEDIUM; else -> Level.HIGH }
        val jpeg = block > 1.2f
        val old = (sat < 0.09f && (contrast != Level.HIGH || noise != Level.LOW)) ||
            (sat < 0.18f && contrast == Level.LOW && noise != Level.LOW)
        val minSide = minOf(bmp.width, bmp.height)
        val portrait = faces.isNotEmpty() && faces.maxOf { it.size }.toFloat() / minSide > 0.18f

        var score = 100
        score -= when (blur) { Level.HIGH -> 25; Level.MEDIUM -> 10; Level.LOW -> 0 }
        score -= when (noise) { Level.HIGH -> 25; Level.MEDIUM -> 10; Level.LOW -> 0 }
        score -= when (resolution) { Level.LOW -> 20; Level.MEDIUM -> 5; Level.HIGH -> 0 }
        if (exposure != Exposure.NORMAL) score -= 10
        if (jpeg) score -= 10
        if (contrast == Level.LOW) score -= 5

        EnhancementProfile(
            width = bmp.width, height = bmp.height,
            resolution = resolution, noise = noise, blur = blur, exposure = exposure, contrast = contrast,
            faces = faces, isPortrait = portrait, isOldPhoto = old, hasJpegArtifacts = jpeg,
            qualityScore = score.coerceIn(0, 100),
            recommendedScale = if (longSide < 1200) 4 else 2,
        )
    }
}
