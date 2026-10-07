package com.pureenhance.ai.ai.postprocessing

object TensorPostprocessor {
    private fun to8(v: Float): Int = (v * 255f + 0.5f).toInt().coerceIn(0, 255)

    /**
     * Converts a rectangle of a planar-RGB tensor ([size]×[size]) into ARGB ints.
     * [down] averages down×down blocks (box filter) — used to get 2× output from the x4 network.
     */
    fun cropToArgb(
        chw: FloatArray, size: Int,
        cropX: Int, cropY: Int, cropW: Int, cropH: Int,
        down: Int, signed: Boolean,
    ): IntArray {
        val plane = size * size
        val outW = cropW / down
        val outH = cropH / down
        val out = IntArray(outW * outH)
        val inv = 1f / (down * down)
        for (oy in 0 until outH) {
            for (ox in 0 until outW) {
                var r = 0f; var g = 0f; var b = 0f
                for (dy in 0 until down) {
                    val rowBase = (cropY + oy * down + dy) * size + cropX + ox * down
                    for (dx in 0 until down) {
                        val i = rowBase + dx
                        r += chw[i]; g += chw[plane + i]; b += chw[2 * plane + i]
                    }
                }
                r *= inv; g *= inv; b *= inv
                if (signed) { r = r * 0.5f + 0.5f; g = g * 0.5f + 0.5f; b = b * 0.5f + 0.5f }
                out[oy * outW + ox] = (0xFF shl 24) or (to8(r) shl 16) or (to8(g) shl 8) or to8(b)
            }
        }
        return out
    }
}
