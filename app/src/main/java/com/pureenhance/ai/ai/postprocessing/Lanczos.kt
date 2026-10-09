package com.pureenhance.ai.ai.postprocessing

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/** Lanczos-3 upscaler (separable, streamed row by row). Takes seconds instead of minutes; no AI model needed. */
object Lanczos {
    private const val A = 3
    private const val TAPS = 6

    private fun kernel(x: Float): Float {
        val ax = abs(x)
        if (ax < 1e-6f) return 1f
        if (ax >= A) return 0f
        val px = Math.PI.toFloat() * ax
        return A * sin(px) * sin(px / A) / (px * px)
    }

    /** Precomputed taps for one axis (upscaling only, dst >= src). */
    class Axis(val srcSize: Int, val dstSize: Int) {
        val start = IntArray(dstSize)
        val weights: Array<FloatArray>

        init {
            val scale = dstSize.toFloat() / srcSize
            weights = Array(dstSize) { d ->
                val c = (d + 0.5f) / scale - 0.5f
                val f = floor(c).toInt() - 2
                start[d] = f
                val w = FloatArray(TAPS) { k -> kernel(c - (f + k)) }
                val s = w.sum()
                for (k in 0 until TAPS) w[k] /= s
                w
            }
        }
    }

    /** Horizontally resamples one ARGB row into planar-interleaved RGB floats (dstW*3). */
    fun horizontalRow(row: IntArray, axis: Axis): FloatArray {
        val out = FloatArray(axis.dstSize * 3)
        val last = axis.srcSize - 1
        for (dx in 0 until axis.dstSize) {
            val f = axis.start[dx]
            val w = axis.weights[dx]
            var r = 0f; var g = 0f; var b = 0f
            for (k in 0 until TAPS) {
                val p = row[(f + k).coerceIn(0, last)]
                r += w[k] * ((p shr 16) and 255)
                g += w[k] * ((p shr 8) and 255)
                b += w[k] * (p and 255)
            }
            out[dx * 3] = r; out[dx * 3 + 1] = g; out[dx * 3 + 2] = b
        }
        return out
    }

    fun resize(src: Bitmap, scale: Int, check: () -> Unit = {}): Bitmap {
        val dw = src.width * scale
        val dh = src.height * scale
        val dst = Bitmap.createBitmap(dw, dh, Bitmap.Config.ARGB_8888)
        val hAxis = Axis(src.width, dw)
        val vAxis = Axis(src.height, dh)
        val srcRow = IntArray(src.width)
        val cache = HashMap<Int, FloatArray>()
        val acc = FloatArray(dw * 3)
        val batchRows = 16
        val batch = IntArray(dw * batchRows)
        var batchStart = 0
        var inBatch = 0
        for (dy in 0 until dh) {
            if (dy % 64 == 0) check()
            val f = vAxis.start[dy]
            val minKeep = f.coerceAtLeast(0)
            cache.keys.removeAll { it < minKeep }
            java.util.Arrays.fill(acc, 0f)
            val w = vAxis.weights[dy]
            for (k in 0 until TAPS) {
                val sy = (f + k).coerceIn(0, src.height - 1)
                val h = cache.getOrPut(sy) {
                    src.getPixels(srcRow, 0, src.width, 0, sy, src.width, 1)
                    horizontalRow(srcRow, hAxis)
                }
                val wk = w[k]
                for (i in acc.indices) acc[i] += wk * h[i]
            }
            val o = inBatch * dw
            for (x in 0 until dw) {
                batch[o + x] = (0xFF shl 24) or
                    (acc[x * 3].roundToInt().coerceIn(0, 255) shl 16) or
                    (acc[x * 3 + 1].roundToInt().coerceIn(0, 255) shl 8) or
                    acc[x * 3 + 2].roundToInt().coerceIn(0, 255)
            }
            inBatch++
            if (inBatch == batchRows || dy == dh - 1) {
                dst.setPixels(batch, 0, dw, 0, batchStart, dw, inBatch)
                batchStart += inBatch
                inBatch = 0
            }
        }
        return dst
    }
}
