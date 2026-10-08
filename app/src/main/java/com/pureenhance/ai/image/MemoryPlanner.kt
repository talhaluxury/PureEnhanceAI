package com.pureenhance.ai.image

import com.pureenhance.ai.utilities.DeviceTier
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sqrt

data class ProcessingPlan(
    val scale: Int,
    val inputWidth: Int,
    val inputHeight: Int,
    val downgradedScale: Boolean,
    val inputReduced: Boolean,
) {
    val outputWidth: Int get() = inputWidth * scale
    val outputHeight: Int get() = inputHeight * scale

    fun message(requestedScale: Int): String? = when {
        inputReduced ->
            "This photo is too large for full-resolution processing on this device, so optimized mode was used " +
                "(output ${outputWidth}×${outputHeight})."
        downgradedScale -> "${requestedScale}× needs more memory than this device has free right now, so 2× was used."
        else -> null
    }
}

/** Pure arithmetic (no Android types) so it can be unit tested on the JVM. */
object MemoryPlanner {
    /** Live bitmaps during a job: output + preview copy + edit copy ≈ 2.5 × 4 bytes per output pixel. */
    private const val BYTES_PER_OUTPUT_PIXEL_BUDGET = 10.0

    fun maxOutputPixels(tier: DeviceTier, availBytes: Long, qualityFactor: Double): Long {
        val tierCap = when (tier) {
            DeviceTier.LOW -> 8_000_000L
            DeviceTier.MID -> 16_000_000L
            DeviceTier.HIGH -> 36_000_000L
            DeviceTier.ULTRA -> 64_000_000L
        }
        val byRam = (availBytes * 0.30 / BYTES_PER_OUTPUT_PIXEL_BUDGET).toLong()
        return min((tierCap * qualityFactor).toLong(), byRam).coerceAtLeast(1_000_000L)
    }

    fun plan(srcW: Int, srcH: Int, requestedScale: Int, maxOutPixels: Long): ProcessingPlan {
        val srcPx = srcW.toLong() * srcH
        val candidates = listOf(requestedScale, 2).filter { it <= requestedScale }.distinct()
        for (s in candidates) {
            if (srcPx * s * s <= maxOutPixels) {
                return ProcessingPlan(s, srcW, srcH, downgradedScale = s != requestedScale, inputReduced = false)
            }
        }
        // Even 2× doesn't fit: shrink the input so that 2× output fits the budget.
        val s = 2
        val factor = sqrt(maxOutPixels.toDouble() / (srcPx * s * s))
        val w = floor(srcW * factor).toInt().coerceAtLeast(16)
        val h = floor(srcH * factor).toInt().coerceAtLeast(16)
        return ProcessingPlan(s, w, h, downgradedScale = requestedScale != s, inputReduced = true)
    }

    /** Inference time grows with the number of *input* pixels, so each quality level caps them. */
    fun capInput(plan: ProcessingPlan, maxInputPixels: Long): ProcessingPlan {
        val px = plan.inputWidth.toLong() * plan.inputHeight
        if (px <= maxInputPixels) return plan
        val f = sqrt(maxInputPixels.toDouble() / px)
        return plan.copy(
            inputWidth = floor(plan.inputWidth * f).toInt().coerceAtLeast(16),
            inputHeight = floor(plan.inputHeight * f).toInt().coerceAtLeast(16),
        )
    }

    fun recommendedScale(longSide: Int): Int = if (longSide < 1200) 4 else 2
}
