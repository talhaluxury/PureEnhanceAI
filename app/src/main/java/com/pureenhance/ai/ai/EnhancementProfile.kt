package com.pureenhance.ai.ai

enum class Level { LOW, MEDIUM, HIGH }
enum class Exposure { DARK, NORMAL, BRIGHT }

data class FaceInfo(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val size: Int get() = maxOf(width, height)
}

/**
 * Internal enhancement profile. For [resolution], LOW means "low resolution" (needs upscaling);
 * for [noise] and [blur], HIGH means "very noisy" / "very blurry"; for [contrast], LOW means flat/faded.
 */
data class EnhancementProfile(
    val width: Int,
    val height: Int,
    val resolution: Level,
    val noise: Level,
    val blur: Level,
    val exposure: Exposure,
    val contrast: Level,
    val faces: List<FaceInfo>,
    val isPortrait: Boolean,
    val isOldPhoto: Boolean,
    val hasJpegArtifacts: Boolean,
    val qualityScore: Int,
    val recommendedScale: Int,
) {
    fun toJson(): String = buildString {
        append("{\n")
        append("  \"resolution\": \"${resolution.name.lowercase()}\",\n")
        append("  \"noise\": \"${noise.name.lowercase()}\",\n")
        append("  \"blur\": \"${blur.name.lowercase()}\",\n")
        append("  \"exposure\": \"${exposure.name.lowercase()}\",\n")
        append("  \"contrast\": \"${contrast.name.lowercase()}\",\n")
        append("  \"faces\": ${faces.size},\n")
        append("  \"portrait\": $isPortrait,\n")
        append("  \"old_photo\": $isOldPhoto,\n")
        append("  \"jpeg_artifacts\": $hasJpegArtifacts,\n")
        append("  \"quality_score\": $qualityScore,\n")
        append("  \"recommended_scale\": $recommendedScale\n")
        append("}")
    }
}
