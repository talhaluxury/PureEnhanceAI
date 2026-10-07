package com.pureenhance.ai.ai.preprocessing

import android.graphics.Bitmap
import kotlin.math.min

object TensorPreprocessor {
    /**
     * Reads a [size]×[size] window at ([ox],[oy]) as planar RGB floats in 0..1.
     * Pixels outside the bitmap are edge-replicated (only happens for images smaller than a tile).
     */
    fun window(src: Bitmap, ox: Int, oy: Int, size: Int): FloatArray {
        val vw = min(size, src.width - ox)
        val vh = min(size, src.height - oy)
        val px = IntArray(vw * vh)
        src.getPixels(px, 0, vw, ox, oy, vw, vh)
        return windowFromPixels(px, vw, vh, size)
    }

    /** Pure version (JVM-testable): pads [px] (vw×vh) to size×size by edge replication. */
    fun windowFromPixels(px: IntArray, vw: Int, vh: Int, size: Int): FloatArray {
        val plane = size * size
        val out = FloatArray(3 * plane)
        for (y in 0 until size) {
            val row = min(y, vh - 1) * vw
            for (x in 0 until size) {
                val p = px[row + min(x, vw - 1)]
                val i = y * size + x
                out[i] = ((p shr 16) and 0xFF) / 255f
                out[plane + i] = ((p shr 8) and 0xFF) / 255f
                out[2 * plane + i] = (p and 0xFF) / 255f
            }
        }
        return out
    }

    /** Planar RGB. [signed] maps to −1..1 (GFPGAN), otherwise 0..1. */
    fun fromPixels(px: IntArray, size: Int, signed: Boolean): FloatArray {
        val plane = size * size
        val out = FloatArray(3 * plane)
        for (i in 0 until plane) {
            val p = px[i]
            var r = ((p shr 16) and 0xFF) / 255f
            var g = ((p shr 8) and 0xFF) / 255f
            var b = (p and 0xFF) / 255f
            if (signed) { r = r * 2f - 1f; g = g * 2f - 1f; b = b * 2f - 1f }
            out[i] = r; out[plane + i] = g; out[2 * plane + i] = b
        }
        return out
    }
}
