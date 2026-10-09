package com.pureenhance.ai

import com.pureenhance.ai.ai.postprocessing.Lanczos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanczosTest {
    @Test fun weightsAreNormalised() {
        for ((s, d) in listOf(10 to 20, 7 to 28, 1 to 4)) {
            for (w in Lanczos.Axis(s, d).weights) assertEquals(1f, w.sum(), 1e-4f)
        }
    }

    @Test fun constantColourStaysConstant() {
        val row = IntArray(6) { 0xFF336699.toInt() }
        val out = Lanczos.horizontalRow(row, Lanczos.Axis(6, 12))
        for (i in 0 until 12) {
            assertEquals(51f, out[i * 3], 0.01f); assertEquals(102f, out[i * 3 + 1], 0.01f); assertEquals(153f, out[i * 3 + 2], 0.01f)
        }
    }

    @Test fun rampStaysMonotonicWhenUpscaled() {
        val row = IntArray(8) { val v = it * 30; (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
        val out = Lanczos.horizontalRow(row, Lanczos.Axis(8, 32))
        for (i in 1 until 32) assertTrue(out[i * 3] >= out[(i - 1) * 3] - 0.5f)
    }
}
