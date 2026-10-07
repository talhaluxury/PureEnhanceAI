package com.pureenhance.ai

import com.pureenhance.ai.image.ImageLoader
import com.pureenhance.ai.image.MemoryPlanner
import com.pureenhance.ai.utilities.DeviceProfile
import com.pureenhance.ai.utilities.DeviceTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryPlannerTest {
    private val gb = 1_073_741_824L

    @Test fun deviceTiersMatchRamClasses() {
        assertEquals(DeviceTier.LOW, DeviceProfile.tierFor((1.8 * gb).toLong()))   // "2 GB"
        assertEquals(DeviceTier.MID, DeviceProfile.tierFor((3.7 * gb).toLong()))   // "4 GB"
        assertEquals(DeviceTier.HIGH, DeviceProfile.tierFor((5.6 * gb).toLong()))  // "6 GB"
        assertEquals(DeviceTier.ULTRA, DeviceProfile.tierFor((7.5 * gb).toLong())) // "8 GB"
    }

    @Test fun smallPhotoKeepsRequested4x() {
        val max = MemoryPlanner.maxOutputPixels(DeviceTier.MID, 2 * gb, 0.75)
        val p = MemoryPlanner.plan(800, 600, 4, max)
        assertEquals(4, p.scale); assertFalse(p.downgradedScale); assertFalse(p.inputReduced)
    }

    @Test fun largePhotoFallsBackTo2xThenShrinksInput() {
        val max = MemoryPlanner.maxOutputPixels(DeviceTier.LOW, 1 * gb, 0.5) // ~3 MP on a 2 GB phone
        val p2 = MemoryPlanner.plan(1600, 900, 4, max)
        assertEquals(2, p2.scale); assertTrue(p2.downgradedScale)
        val p3 = MemoryPlanner.plan(6000, 4000, 4, max)
        assertEquals(2, p3.scale); assertTrue(p3.inputReduced)
        assertTrue(p3.outputWidth.toLong() * p3.outputHeight <= max)
    }

    @Test fun neverExceedsBudgetAcrossDevices() {
        for (tier in DeviceTier.entries) for (q in listOf(0.5, 0.75, 1.0)) for ((w, h) in listOf(640 to 480, 4000 to 3000, 12000 to 9000)) {
            val max = MemoryPlanner.maxOutputPixels(tier, 3 * gb, q)
            val p = MemoryPlanner.plan(w, h, 4, max)
            assertTrue(p.outputWidth.toLong() * p.outputHeight <= max + p.outputWidth + p.outputHeight)
        }
    }

    @Test fun highEndGetsMoreThanLowEnd() {
        assertTrue(MemoryPlanner.maxOutputPixels(DeviceTier.ULTRA, 6 * gb, 1.0) > MemoryPlanner.maxOutputPixels(DeviceTier.LOW, 1 * gb, 1.0))
    }

    @Test fun sampleSizeNeverGoesBelowTarget() {
        assertEquals(1, ImageLoader.sampleSizeFor(1000, 1000, 1000, 1000))
        assertEquals(4, ImageLoader.sampleSizeFor(4000, 3000, 1000, 700))
        assertEquals(1, ImageLoader.sampleSizeFor(4000, 3000, 3000, 2500))
    }
}
