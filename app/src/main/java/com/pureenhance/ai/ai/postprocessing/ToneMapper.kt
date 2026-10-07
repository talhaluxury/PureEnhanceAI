package com.pureenhance.ai.ai.postprocessing

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

data class ToneParams(
    val levels: Levels?,
    val levelStrength: Float,
    val exposure: Float,    // −1..1
    val contrast: Float,    // −1..1
    val saturation: Float,  // −1..1
    val warmth: Float,      // −1..1
) {
    val usesLevels: Boolean get() = levels != null && levelStrength > 0.01f
    val isIdentity: Boolean
        get() = !usesLevels && abs(exposure) < 0.005f && abs(contrast) < 0.005f &&
            abs(saturation) < 0.005f && abs(warmth) < 0.005f
}

/** Subtle, LUT-based colour/exposure refinement applied in place, block by block. */
object ToneMapper {
    fun applyInPlace(bmp: Bitmap, p: ToneParams) {
        if (p.isIdentity) return
        val lutR = buildLut(p, 0)
        val lutG = buildLut(p, 1)
        val lutB = buildLut(p, 2)
        val sat = 1f + 0.6f * p.saturation
        val doSat = abs(p.saturation) >= 0.005f
        val w = bmp.width
        val rows = (1_000_000 / w).coerceIn(1, bmp.height)
        val buf = IntArray(w * rows)
        var y = 0
        while (y < bmp.height) {
            val h = minOf(rows, bmp.height - y)
            bmp.getPixels(buf, 0, w, 0, y, w, h)
            for (i in 0 until w * h) {
                val c = buf[i]
                var r = lutR[(c shr 16) and 255]
                var g = lutG[(c shr 8) and 255]
                var b = lutB[c and 255]
                if (doSat) {
                    val gray = 0.299f * r + 0.587f * g + 0.114f * b
                    r = (gray + (r - gray) * sat).coerceIn(0f, 255f)
                    g = (gray + (g - gray) * sat).coerceIn(0f, 255f)
                    b = (gray + (b - gray) * sat).coerceIn(0f, 255f)
                }
                buf[i] = (c and 0xFF000000.toInt()) or (r.roundToInt() shl 16) or (g.roundToInt() shl 8) or b.roundToInt()
            }
            bmp.setPixels(buf, 0, w, 0, y, w, h)
            y += h
        }
    }

    /** Returns a 256-entry LUT in 0..255 (as floats so saturation can be applied without re-rounding). */
    fun buildLut(p: ToneParams, channel: Int): FloatArray {
        val lv = if (p.usesLevels) p.levels!!.lut(p.levelStrength) else null
        val gain = 2f.pow(0.8f * p.exposure)
        val k = 1f + 0.6f * p.contrast
        val warm = when (channel) { 0 -> 1f + 0.10f * p.warmth; 2 -> 1f - 0.10f * p.warmth; else -> 1f }
        return FloatArray(256) { i ->
            var v = lv?.get(i) ?: (i / 255f)
            v *= gain
            v = (v - 0.5f) * k + 0.5f
            v *= warm
            v.coerceIn(0f, 1f) * 255f
        }
    }
}
