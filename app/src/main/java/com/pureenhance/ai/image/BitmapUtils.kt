package com.pureenhance.ai.image

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.roundToInt

object BitmapUtils {
    /** Returns [src] itself when it already fits, otherwise a high-quality (stepwise halved) copy. */
    fun fitWithin(src: Bitmap, maxSide: Int): Bitmap {
        val longSide = max(src.width, src.height)
        if (longSide <= maxSide) return src
        val s = maxSide.toFloat() / longSide
        return resize(src, (src.width * s).roundToInt().coerceAtLeast(1), (src.height * s).roundToInt().coerceAtLeast(1))
    }

    /** Stepwise halving avoids the aliasing a single bilinear pass produces on big reductions. */
    fun resize(src: Bitmap, newW: Int, newH: Int): Bitmap {
        var cur = src
        while (cur.width / 2 >= newW && cur.height / 2 >= newH) {
            val half = Bitmap.createScaledBitmap(cur, cur.width / 2, cur.height / 2, true)
            if (cur !== src) cur.recycle()
            cur = half
        }
        if (cur.width == newW && cur.height == newH) return cur
        val out = Bitmap.createScaledBitmap(cur, newW, newH, true)
        if (cur !== src && out !== cur) cur.recycle()
        return out
    }
}
