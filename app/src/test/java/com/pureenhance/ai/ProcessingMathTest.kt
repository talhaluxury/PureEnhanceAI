package com.pureenhance.ai

import com.pureenhance.ai.ai.EnhancementProfile
import com.pureenhance.ai.ai.ImageMetrics
import com.pureenhance.ai.ai.Level
import com.pureenhance.ai.ai.Exposure
import com.pureenhance.ai.ai.face.FaceRestorer
import com.pureenhance.ai.ai.postprocessing.AutoLevels
import com.pureenhance.ai.ai.postprocessing.TensorPostprocessor
import com.pureenhance.ai.ai.preprocessing.TensorPreprocessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class ProcessingMathTest {
    private fun gray(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v

    @Test fun windowReplicatesEdgesForSmallImages() {
        val px = intArrayOf(gray(0), gray(255)) // 2×1 image
        val t = TensorPreprocessor.windowFromPixels(px, 2, 1, 4)
        val plane = 16
        for (y in 0 until 4) {
            assertEquals(0f, t[y * 4], 1e-6f); assertEquals(1f, t[y * 4 + 1], 1e-6f)
            assertEquals(1f, t[y * 4 + 3], 1e-6f)
        }
        assertEquals(t[0], t[plane], 0f) // grey → R == G
    }

    @Test fun cropAveragesBlocksAndClamps() {
        val size = 4
        val chw = FloatArray(3 * size * size) { 1.5f } // out of range on purpose
        val out = TensorPostprocessor.cropToArgb(chw, size, 0, 0, 4, 4, 2, signed = false)
        assertEquals(4, out.size)
        assertEquals(0xFFFFFFFF.toInt(), out[0])
        val avg = FloatArray(3 * 16) { if ((it % 16) % 2 == 0) 0f else 1f }
        val o2 = TensorPostprocessor.cropToArgb(avg, 4, 0, 0, 4, 4, 2, signed = false)
        assertEquals(128, o2[0] and 0xFF)
    }

    @Test fun darkHistogramBrightensWithoutBlowingOut() {
        val hist = IntArray(256)
        for (i in 0..60) hist[i] = 100
        val lv = AutoLevels.fromHistogram(hist)
        assertTrue(lv.gamma < 1f && lv.gamma >= 0.7f)
        assertTrue(lv.lo <= 0.12f && lv.hi >= 0.85f)
        val lut = lv.lut(1f)
        for (i in 1..255) assertTrue(lut[i] >= lut[i - 1]) // monotonic
        assertEquals(1f, AutoLevels.fromHistogram(IntArray(256) { 10 }).gamma, 1e-6f) // balanced image untouched
    }

    @Test fun levelsStrengthZeroIsIdentity() {
        val lut = AutoLevels.fromHistogram(IntArray(256).also { for (i in 0..60) it[i] = 5 }).lut(0f)
        for (i in 0..255) assertEquals(i / 255f, lut[i], 1e-5f)
    }

    @Test fun noiseEstimateRisesWithNoise() {
        val w = 128; val h = 128; val r = Random(1)
        val clean = FloatArray(w * h) { 120f }
        val noisy = FloatArray(w * h) { 120f + (r.nextGaussian() * 12).toFloat() }
        assertTrue(ImageMetrics.noiseSigma(clean, w, h) < 0.5f)
        assertEquals(12f, ImageMetrics.noiseSigma(noisy, w, h), 2.5f)
    }

    @Test fun blurLowersLaplacianVariance() {
        val w = 64; val h = 64
        val sharp = FloatArray(w * h) { if ((it % w) / 4 % 2 == 0) 0f else 255f }
        val soft = FloatArray(w * h) { 127f + 20f * kotlin.math.sin((it % w) / 6f) }
        assertTrue(ImageMetrics.laplacianVariance(sharp, w, h) > ImageMetrics.laplacianVariance(soft, w, h) * 10)
    }

    @Test fun blockinessDetectsEightPixelSteps() {
        val w = 64; val h = 16
        val blocky = FloatArray(w * h) { ((it % w) / 8 % 2) * 6f + 100f }
        val smooth = FloatArray(w * h) { 100f + (it % w) * 0.1f }
        assertTrue(ImageMetrics.blockiness(blocky, w, h) > 1.5f)
        assertTrue(ImageMetrics.blockiness(smooth, w, h) < 1.3f)
    }

    @Test fun faceGateIsConservativeForTinyFaces() {
        assertEquals(0f, FaceRestorer.gateFor(30), 0f)
        assertTrue(FaceRestorer.gateFor(60) < FaceRestorer.gateFor(100))
        assertEquals(0.3f, FaceRestorer.gateFor(300), 0f) // large faces: light touch only
    }

    @Test fun profileJsonMatchesSpecShape() {
        val p = EnhancementProfile(300, 200, Level.LOW, Level.HIGH, Level.MEDIUM, Exposure.NORMAL, Level.MEDIUM, emptyList(), false, false, false, 40, 4)
        val json = p.toJson()
        assertTrue(json.contains("\"resolution\": \"low\"") && json.contains("\"noise\": \"high\"") && json.contains("\"recommended_scale\": 4"))
    }
}
