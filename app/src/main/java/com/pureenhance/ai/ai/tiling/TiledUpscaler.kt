package com.pureenhance.ai.ai.tiling

import android.graphics.Bitmap
import com.pureenhance.ai.ai.inference.TileInference
import com.pureenhance.ai.ai.postprocessing.TensorPostprocessor
import com.pureenhance.ai.ai.preprocessing.TensorPreprocessor
import com.pureenhance.ai.utilities.EnhanceException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/**
 * Runs a fixed-size tile network over an arbitrarily large bitmap with bounded memory:
 * only one tile tensor is alive at a time and the output bitmap is filled tile by tile.
 *
 * For [outScale] smaller than the network scale (2× from an x4 model) every tile is box-averaged down,
 * which gives clean super-sampled results without a second inference pass.
 */
class TiledUpscaler(
    private val inference: TileInference,
    private val tileSize: Int,
    private val throttleMs: () -> Long = { 0L },
) {
    suspend fun upscale(src: Bitmap, outScale: Int, pad: Int, onProgress: (Float) -> Unit): Bitmap {
        val m = inference.scale
        require(outScale in 1..m && m % outScale == 0) { "unsupported scale $outScale for model x$m" }
        val down = m / outScale
        val dst = try {
            Bitmap.createBitmap(src.width * outScale, src.height * outScale, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            throw EnhanceException.OutOfMemory(e)
        }
        try {
            val tiles = TilePlanner.plan(src.width, src.height, tileSize, pad)
            val outSize = tileSize * m
            for ((i, t) in tiles.withIndex()) {
                currentCoroutineContext().ensureActive()
                val wait = throttleMs()
                if (wait > 0) delay(wait)
                val input = TensorPreprocessor.window(src, t.originX, t.originY, tileSize)
                val out = inference.infer(input, tileSize)
                val px = TensorPostprocessor.cropToArgb(
                    out, outSize,
                    (t.coreX - t.originX) * m, (t.coreY - t.originY) * m, t.coreW * m, t.coreH * m,
                    down, signed = false,
                )
                val w = t.coreW * outScale
                dst.setPixels(px, 0, w, t.coreX * outScale, t.coreY * outScale, w, t.coreH * outScale)
                onProgress((i + 1f) / tiles.size)
            }
            return dst
        } catch (t: Throwable) {
            dst.recycle()
            throw t
        }
    }
}
