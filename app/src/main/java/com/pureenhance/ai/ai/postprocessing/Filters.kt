package com.pureenhance.ai.ai.postprocessing

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

/** Streams a bitmap through a per-pixel neighbourhood function in 64-row blocks (low memory). */
object BlockFilter {
    const val BLOCK_ROWS = 64

    inline fun run(
        src: Bitmap,
        halo: Int,
        check: () -> Unit,
        pixel: (buf: IntArray, bufRow0: Int, w: Int, h: Int, x: Int, y: Int) -> Int,
    ): Bitmap {
        val w = src.width
        val h = src.height
        val dst = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val inBuf = IntArray(w * (BLOCK_ROWS + 2 * halo))
        val outBuf = IntArray(w * BLOCK_ROWS)
        var y0 = 0
        while (y0 < h) {
            check()
            val y1 = minOf(h, y0 + BLOCK_ROWS)
            val r0 = maxOf(0, y0 - halo)
            val r1 = minOf(h, y1 + halo)
            src.getPixels(inBuf, 0, w, 0, r0, w, r1 - r0)
            for (y in y0 until y1) {
                for (x in 0 until w) outBuf[(y - y0) * w + x] = pixel(inBuf, r0, w, h, x, y)
            }
            dst.setPixels(outBuf, 0, w, 0, y0, w, y1 - y0)
            y0 = y1
        }
        return dst
    }
}

/** Edge-preserving 5×5 bilateral filter. Removes grain/JPEG blocking without smearing edges. */
object BilateralDenoiser {
    fun apply(src: Bitmap, strength: Float, check: () -> Unit = {}): Bitmap {
        if (strength < 0.02f) return src
        val sigmaR = 6f + 34f * strength.coerceAtMost(1f)
        val rangeW = FloatArray(766) { val d = it / 3f; exp(-(d * d) / (2f * sigmaR * sigmaR)) }
        val spatial = FloatArray(25) {
            val dx = it % 5 - 2
            val dy = it / 5 - 2
            exp(-(dx * dx + dy * dy) / (2f * 1.5f * 1.5f))
        }
        return BlockFilter.run(src, 2, check) { buf, r0, w, h, x, y ->
            val c = buf[(y - r0) * w + x]
            val cr = (c shr 16) and 255
            val cg = (c shr 8) and 255
            val cb = c and 255
            var sr = 0f; var sg = 0f; var sb = 0f; var sw = 0f
            for (dy in -2..2) {
                val rowBase = ((y + dy).coerceIn(0, h - 1) - r0) * w
                for (dx in -2..2) {
                    val p = buf[rowBase + (x + dx).coerceIn(0, w - 1)]
                    val pr = (p shr 16) and 255
                    val pg = (p shr 8) and 255
                    val pb = p and 255
                    val wt = spatial[(dy + 2) * 5 + dx + 2] * rangeW[abs(pr - cr) + abs(pg - cg) + abs(pb - cb)]
                    sr += pr * wt; sg += pg * wt; sb += pb * wt; sw += wt
                }
            }
            (0xFF shl 24) or ((sr / sw).roundToInt() shl 16) or ((sg / sw).roundToInt() shl 8) or (sb / sw).roundToInt()
        }
    }
}

/** Removes dust specks and hairline scratches: pixels that differ strongly from their 3×3 median are replaced. */
object DamageReducer {
    fun apply(src: Bitmap, threshold: Int = 28, check: () -> Unit = {}): Bitmap {
        val keys = IntArray(9)
        val pix = IntArray(9)
        return BlockFilter.run(src, 1, check) { buf, r0, w, h, x, y ->
            var n = 0
            for (dy in -1..1) {
                val rowBase = ((y + dy).coerceIn(0, h - 1) - r0) * w
                for (dx in -1..1) {
                    val p = buf[rowBase + (x + dx).coerceIn(0, w - 1)]
                    val l = (299 * ((p shr 16) and 255) + 587 * ((p shr 8) and 255) + 114 * (p and 255)) / 1000
                    var j = n
                    while (j > 0 && keys[j - 1] > l) { keys[j] = keys[j - 1]; pix[j] = pix[j - 1]; j-- }
                    keys[j] = l; pix[j] = p
                    n++
                }
            }
            val c = buf[(y - r0) * w + x]
            val lc = (299 * ((c shr 16) and 255) + 587 * ((c shr 8) and 255) + 114 * (c and 255)) / 1000
            if (abs(lc - keys[4]) > threshold) pix[4] or (0xFF shl 24) else c
        }
    }
}

/** Gentle 3×3 unsharp mask. */
object Sharpener {
    private val SHIFTS = intArrayOf(16, 8, 0)

    fun apply(src: Bitmap, amount: Float, check: () -> Unit = {}): Bitmap {
        if (amount < 0.02f) return src
        val k = amount * 1.2f
        return BlockFilter.run(src, 1, check) { buf, r0, w, h, x, y ->
            val xm = (x - 1).coerceAtLeast(0)
            val xp = (x + 1).coerceAtMost(w - 1)
            val ym = ((y - 1).coerceAtLeast(0) - r0) * w
            val yc = (y - r0) * w
            val yp = ((y + 1).coerceAtMost(h - 1) - r0) * w
            var out = 0xFF shl 24
            for (s in SHIFTS) {
                val c = (buf[yc + x] shr s) and 255
                val blur = (
                    ((buf[ym + xm] shr s) and 255) + 2 * ((buf[ym + x] shr s) and 255) + ((buf[ym + xp] shr s) and 255) +
                        2 * ((buf[yc + xm] shr s) and 255) + 4 * c + 2 * ((buf[yc + xp] shr s) and 255) +
                        ((buf[yp + xm] shr s) and 255) + 2 * ((buf[yp + x] shr s) and 255) + ((buf[yp + xp] shr s) and 255)
                    ) / 16f
                out = out or ((c + k * (c - blur)).roundToInt().coerceIn(0, 255) shl s)
            }
            out
        }
    }
}
