package com.pureenhance.ai.ai.postprocessing

import android.graphics.Bitmap
import kotlin.math.ln
import kotlin.math.pow

/** Black point, white point and gamma estimated from the image histogram. */
data class Levels(val lo: Float, val hi: Float, val gamma: Float) {
    /** 256-entry float LUT (0..1). [strength] 0 = identity, 1 = full correction. */
    fun lut(strength: Float): FloatArray {
        val s = strength.coerceIn(0f, 1.5f)
        val l = lo * s
        val h = 1f - (1f - hi) * s
        val g = gamma.pow(s)
        val range = (h - l).coerceAtLeast(0.05f)
        return FloatArray(256) { i -> ((i / 255f - l) / range).coerceIn(0f, 1f).pow(g) }
    }

    companion object {
        val IDENTITY = Levels(0f, 1f, 1f)
    }
}

object AutoLevels {
    fun compute(bmp: Bitmap): Levels {
        val hist = IntArray(256)
        val total = bmp.width.toLong() * bmp.height
        val stride = maxOf(1, kotlin.math.sqrt(total / 200_000.0).toInt())
        val row = IntArray(bmp.width)
        var y = 0
        while (y < bmp.height) {
            bmp.getPixels(row, 0, bmp.width, 0, y, bmp.width, 1)
            var x = 0
            while (x < bmp.width) {
                val p = row[x]
                val l = (0.299f * ((p shr 16) and 255) + 0.587f * ((p shr 8) and 255) + 0.114f * (p and 255)).toInt()
                hist[l.coerceIn(0, 255)]++
                x += stride
            }
            y += stride
        }
        return fromHistogram(hist)
    }

    /** Conservative on purpose: clip at most 0.5 %, limit stretch, limit gamma. */
    fun fromHistogram(hist: IntArray): Levels {
        val total = hist.sum().toDouble()
        if (total <= 0) return Levels.IDENTITY
        fun percentile(p: Double): Int {
            var acc = 0.0
            for (i in 0..255) { acc += hist[i]; if (acc >= p * total) return i }
            return 255
        }
        val lo = (percentile(0.005) / 255f).coerceAtMost(0.12f)
        val hi = (percentile(0.995) / 255f).coerceAtLeast(0.85f)
        var mean = 0.0
        for (i in 0..255) mean += i * hist[i]
        mean /= total * 255.0
        val m = ((mean - lo) / (hi - lo)).coerceIn(0.03, 0.97)
        val gamma = if (m in 0.40..0.62) 1f else (ln(0.46) / ln(m)).toFloat().coerceIn(0.70f, 1.20f)
        return Levels(lo, hi, gamma)
    }
}
