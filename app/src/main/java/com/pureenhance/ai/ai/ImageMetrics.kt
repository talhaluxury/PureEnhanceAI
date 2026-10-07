package com.pureenhance.ai.ai

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Pure functions over pixel arrays (no Android types) so the analysis can be unit-tested on the JVM. */
object ImageMetrics {
    fun luminance(px: IntArray): FloatArray = FloatArray(px.size) {
        val p = px[it]
        0.299f * ((p shr 16) and 255) + 0.587f * ((p shr 8) and 255) + 0.114f * (p and 255)
    }

    fun meanSaturation(px: IntArray): Float {
        var s = 0.0
        for (p in px) {
            val r = (p shr 16) and 255; val g = (p shr 8) and 255; val b = p and 255
            val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
            s += if (mx == 0) 0.0 else (mx - mn).toDouble() / mx
        }
        return (s / px.size).toFloat()
    }

    /** Variance of the Laplacian; low values mean blur. Computed on ≤1024 px images. */
    fun laplacianVariance(l: FloatArray, w: Int, h: Int): Float {
        if (w < 3 || h < 3) return 0f
        var sum = 0.0; var sumSq = 0.0; var n = 0
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            val v = l[i - w] + l[i + w] + l[i - 1] + l[i + 1] - 4f * l[i]
            sum += v; sumSq += v * v; n++
        }
        val mean = sum / n
        return (sumSq / n - mean * mean).toFloat()
    }

    /** Immerkaer's fast noise-sigma estimate (0..255 scale). */
    fun noiseSigma(l: FloatArray, w: Int, h: Int): Float {
        if (w < 3 || h < 3) return 0f
        var acc = 0.0
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            val n = l[i - w - 1] - 2f * l[i - w] + l[i - w + 1] -
                2f * l[i - 1] + 4f * l[i] - 2f * l[i + 1] +
                l[i + w - 1] - 2f * l[i + w] + l[i + w + 1]
            acc += abs(n)
        }
        return (acc * sqrt(0.5 * Math.PI) / (6.0 * (w - 2) * (h - 2))).toFloat()
    }

    /** Ratio of gradient energy on 8-px block boundaries vs elsewhere. ≈1 for clean images, >1.2 for JPEG blocking. */
    fun blockiness(l: FloatArray, w: Int, h: Int): Float {
        var b = 0.0; var nb = 0L; var o = 0.0; var no = 0L
        for (y in 0 until h) for (x in 1 until w) {
            val d = abs(l[y * w + x] - l[y * w + x - 1])
            if (x % 8 == 0) { b += d; nb++ } else { o += d; no++ }
        }
        if (nb == 0L || no == 0L) return 1f
        return ((b / nb) / (o / no + 1e-3)).toFloat()
    }

    fun histogram(l: FloatArray): IntArray {
        val hist = IntArray(256)
        for (v in l) hist[v.toInt().coerceIn(0, 255)]++
        return hist
    }

    fun percentile(hist: IntArray, p: Double): Float {
        val total = hist.sum().toDouble()
        var acc = 0.0
        for (i in 0..255) { acc += hist[i]; if (acc >= p * total) return i / 255f }
        return 1f
    }

    fun mean(l: FloatArray): Float = (l.sum() / l.size) / 255f
}
