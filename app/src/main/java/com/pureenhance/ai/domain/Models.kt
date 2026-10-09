package com.pureenhance.ai.domain

import android.graphics.Bitmap
import com.pureenhance.ai.ai.EnhancementProfile
import com.pureenhance.ai.ai.face.FaceLayer
import com.pureenhance.ai.ai.postprocessing.Levels
import com.pureenhance.ai.image.ImageInfo

enum class EnhanceMode(val label: String, val hint: String) {
    AUTO("Auto", "Picks the best pipeline"),
    PORTRAIT("Portrait", "Faces and skin"),
    OLD_PHOTO("Old Photo", "Faded, noisy, scratched"),
    LOW_QUALITY("Low Quality", "Noisy or compressed"),
    HD_UPSCALE("HD Upscale", "Resolution only"),
}

enum class Quality(val label: String, val memoryFactor: Double, val tilePad: Int, val maxInputPixels: Long) {
    BALANCED("Fast", 0.50, 8, 1_000_000L),
    HIGH("High", 0.75, 16, 600_000L),
    MAXIMUM("Maximum", 1.00, 24, 1_500_000L),
}

data class EnhanceSettings(val mode: EnhanceMode, val quality: Quality, val scale: Int)

enum class Stage(val label: String) {
    ANALYZING("Analyzing image"),
    RESTORING("Restoring details"),
    FACES("Enhancing faces"),
    UPSCALING("Upscaling image"),
    FINALIZING("Finalizing result"),
}

/** [fraction] is overall progress 0..1 derived from real pipeline work (tiles processed, faces restored). */
data class Progress(val stage: Stage, val fraction: Float, val etaSeconds: Int? = null)

/** Optional subtle controls. Defaults reproduce the pipeline's own result. */
data class EditParams(
    val autoEnhance: Float = 1f,   // 0..1.2
    val faceEnhance: Float = 1f,   // 0..1.3
    val sharpness: Float = 0f,     // 0..1
    val denoise: Float = 0f,       // 0..1
    val exposure: Float = 0f,      // −1..1
    val contrast: Float = 0f,      // −1..1
    val color: Float = 0f,         // −1..1
    val warmth: Float = 0f,        // −1..1
) {
    companion object {
        /** Plain AI super-resolution output, no refinements. */
        val Original = EditParams(autoEnhance = 0f, faceEnhance = 0f)
    }
}

class EnhanceResult(
    /** Full-resolution upscaled image (faces are NOT composited; see [faces]). */
    val base: Bitmap,
    /** ≤2560 px copy of [base] used for responsive previews. */
    val proxy: Bitmap,
    /** ≤2560 px version of the original input for the comparison slider. */
    val before: Bitmap,
    val faces: List<FaceLayer>,
    val inputWidth: Int,
    val levels: Levels,
    val original: ImageInfo,
    val profile: EnhancementProfile,
    val modeUsed: EnhanceMode,
    val notes: List<String>,
    /** What the "Enhanced" preset means for this result (Fast mode adds mild sharpening). */
    val defaultParams: EditParams,
) {
    val outputWidth: Int get() = base.width
    val outputHeight: Int get() = base.height

    fun release() {
        if (proxy !== base) proxy.recycle()
        base.recycle()
        faces.forEach { it.restored.recycle() }
    }
}
