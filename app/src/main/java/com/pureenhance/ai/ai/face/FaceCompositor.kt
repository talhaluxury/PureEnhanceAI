package com.pureenhance.ai.ai.face

import android.graphics.Bitmap
import com.pureenhance.ai.image.BitmapUtils
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Feathered, strength-weighted paste of restored faces onto the upscaled image. */
object FaceCompositor {
    /**
     * @param inputWidth width of the image the [layers] coordinates refer to (so the same layers work
     *        for both the full-size result and the downscaled preview).
     * @param userStrength 0 = faces untouched, 1 = default, up to 1.3 = stronger.
     */
    fun compose(target: Bitmap, layers: List<FaceLayer>, inputWidth: Int, userStrength: Float) {
        if (userStrength <= 0.01f) return
        val scale = target.width.toFloat() / inputWidth
        for (l in layers) {
            val alpha0 = (l.strength * userStrength).coerceIn(0f, 1f)
            if (alpha0 < 0.01f) continue
            val side = (l.side * scale).roundToInt().coerceAtLeast(8)
            val left = (l.cx * scale - side / 2f).roundToInt()
            val top = (l.cy * scale - side / 2f).roundToInt()
            val x0 = maxOf(0, left); val y0 = maxOf(0, top)
            val x1 = minOf(target.width, left + side); val y1 = minOf(target.height, top + side)
            val w = x1 - x0; val h = y1 - y0
            if (w <= 0 || h <= 0) continue

            val face = BitmapUtils.resize(l.restored, side, side)
            val fpx = IntArray(w * h)
            face.getPixels(fpx, 0, w, x0 - left, y0 - top, w, h)
            if (face !== l.restored) face.recycle()
            val bpx = IntArray(w * h)
            target.getPixels(bpx, 0, w, x0, y0, w, h)

            for (y in 0 until h) {
                val ny = ((y0 + y - top) + 0.5f) / side * 2f - 1f
                for (x in 0 until w) {
                    val nx = ((x0 + x - left) + 0.5f) / side * 2f - 1f
                    val r = sqrt(nx * nx + ny * ny)
                    val t = ((r - 0.55f) / 0.40f).coerceIn(0f, 1f)
                    val a = (1f - t * t * (3f - 2f * t)) * alpha0
                    if (a <= 0f) continue
                    val i = y * w + x
                    val b = bpx[i]; val f = fpx[i]
                    val rr = mix((b shr 16) and 255, (f shr 16) and 255, a)
                    val gg = mix((b shr 8) and 255, (f shr 8) and 255, a)
                    val bb = mix(b and 255, f and 255, a)
                    bpx[i] = (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb
                }
            }
            target.setPixels(bpx, 0, w, x0, y0, w, h)
        }
    }

    private fun mix(a: Int, b: Int, t: Float): Int = (a + (b - a) * t).roundToInt().coerceIn(0, 255)
}
