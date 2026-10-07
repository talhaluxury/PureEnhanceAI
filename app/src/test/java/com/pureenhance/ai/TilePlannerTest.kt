package com.pureenhance.ai

import com.pureenhance.ai.ai.tiling.TilePlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TilePlannerTest {
    private fun check(w: Int, h: Int, tile: Int, pad: Int) {
        val tiles = TilePlanner.plan(w, h, tile, pad)
        val cover = Array(h) { IntArray(w) }
        for (t in tiles) {
            // window must be inside the image (unless the image is smaller than a tile) and contain the core
            if (w >= tile) assertTrue(t.originX in 0..(w - tile))
            if (h >= tile) assertTrue(t.originY in 0..(h - tile))
            assertTrue(t.coreX >= t.originX && t.coreX + t.coreW <= t.originX + tile)
            assertTrue(t.coreY >= t.originY && t.coreY + t.coreH <= t.originY + tile)
            for (y in t.coreY until t.coreY + t.coreH) for (x in t.coreX until t.coreX + t.coreW) cover[y][x]++
        }
        for (y in 0 until h) for (x in 0 until w) assertEquals("pixel $x,$y in ${w}x$h", 1, cover[y][x])
    }

    @Test fun coversEveryPixelExactlyOnce() {
        for ((w, h) in listOf(1 to 1, 50 to 40, 128 to 128, 129 to 300, 500 to 333, 1001 to 77)) {
            for (pad in listOf(8, 16, 24)) check(w, h, 128, pad)
        }
    }

    @Test fun contextPaddingIsAtLeastPadInsideTheImage() {
        val pad = 16
        val tiles = TilePlanner.plan(600, 600, 128, pad)
        for (t in tiles) {
            if (t.coreX > 0) assertTrue(t.coreX - t.originX >= pad)
            if (t.coreY > 0) assertTrue(t.coreY - t.originY >= pad)
        }
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsPadThatLeavesNoCore() {
        TilePlanner.plan(100, 100, 128, 64)
    }
}
