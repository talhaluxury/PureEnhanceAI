package com.pureenhance.ai

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pureenhance.ai.ai.inference.TileInference
import com.pureenhance.ai.ai.tiling.TiledUpscaler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** Nearest-neighbour "model": if tiling is correct the result equals a plain nearest upscale → zero seams. */
private class NearestInference(override val scale: Int = 4, private val delayMs: Long = 0) : TileInference {
    var calls = 0
    override fun infer(chw: FloatArray, size: Int): FloatArray {
        calls++
        if (delayMs > 0) Thread.sleep(delayMs)
        val o = size * scale
        val out = FloatArray(3 * o * o)
        for (c in 0 until 3) for (y in 0 until o) for (x in 0 until o)
            out[c * o * o + y * o + x] = chw[c * size * size + (y / scale) * size + (x / scale)]
        return out
    }
}

@RunWith(AndroidJUnit4::class)
class TiledUpscalerInstrumentedTest {
    private fun pattern(w: Int, h: Int): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val px = IntArray(w * h) { val x = it % w; val y = it / w; (0xFF shl 24) or ((x * 7 % 256) shl 16) or ((y * 11 % 256) shl 8) or ((x + y) * 3 % 256) }
        b.setPixels(px, 0, w, 0, 0, w, h)
        return b
    }

    private fun assertNearest(src: Bitmap, out: Bitmap, s: Int) {
        assertEquals(src.width * s, out.width); assertEquals(src.height * s, out.height)
        val a = IntArray(src.width * src.height).also { src.getPixels(it, 0, src.width, 0, 0, src.width, src.height) }
        val b = IntArray(out.width * out.height).also { out.getPixels(it, 0, out.width, 0, 0, out.width, out.height) }
        for (y in 0 until out.height) for (x in 0 until out.width) {
            val e = a[(y / s) * src.width + x / s]
            val g = b[y * out.width + x]
            // allow ±1 per channel for float→8-bit rounding
            for (sh in intArrayOf(16, 8, 0)) assertTrue("seam at $x,$y", kotlin.math.abs(((e shr sh) and 255) - ((g shr sh) and 255)) <= 1)
        }
    }

    @Test fun reconstructsWithoutSeams4xAnd2x() = runBlocking {
        val src = pattern(301, 207)
        for (pad in listOf(8, 16, 24)) {
            val up4 = TiledUpscaler(NearestInference(), 128).upscale(src, 4, pad) {}
            assertNearest(src, up4, 4); up4.recycle()
            val up2 = TiledUpscaler(NearestInference(), 128).upscale(src, 2, pad) {}
            assertNearest(src, up2, 2); up2.recycle()
        }
    }

    @Test fun handlesImagesSmallerThanOneTile() = runBlocking {
        val src = pattern(20, 9)
        assertNearest(src, TiledUpscaler(NearestInference(), 128).upscale(src, 4, 16) {}, 4)
    }

    @Test fun largeImageProcessesTileByTileAndReportsProgress() = runBlocking {
        val src = pattern(1500, 1100)
        val inf = NearestInference()
        var last = 0f
        val out = TiledUpscaler(inf, 128).upscale(src, 2, 16) { last = it }
        assertEquals(1f, last, 0f); assertTrue(inf.calls > 100)
        assertEquals(3000, out.width)
    }

    @Test fun cancellationStopsWorkAndFreesOutput() = runBlocking {
        val src = pattern(1200, 1200)
        val inf = NearestInference(delayMs = 20)
        val job = async(kotlinx.coroutines.Dispatchers.Default) { TiledUpscaler(inf, 128).upscale(src, 2, 16) {} }
        kotlinx.coroutines.delay(150)
        job.cancel()
        try { job.await(); fail("expected cancellation") } catch (e: CancellationException) { /* ok */ }
        val callsAtCancel = inf.calls
        kotlinx.coroutines.delay(150)
        assertTrue("inference kept running after cancel", inf.calls <= callsAtCancel + 1)
        assertTrue(job is Job)
    }
}
