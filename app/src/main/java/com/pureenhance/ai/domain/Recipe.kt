package com.pureenhance.ai.domain

import com.pureenhance.ai.ai.EnhancementProfile
import com.pureenhance.ai.ai.Exposure
import com.pureenhance.ai.ai.Level

/** What the pipeline will actually do for this photo. Derived from mode + analysis + quality. */
data class Recipe(
    val modeUsed: EnhanceMode,
    val denoise: Float,
    val damageReduction: Boolean,
    val faces: Boolean,
    val faceStrength: Float,
    val autoLevels: Float,
) {
    companion object {
        fun resolve(mode: EnhanceMode, p: EnhancementProfile, quality: Quality): Recipe {
            val qf = when (quality) { Quality.BALANCED -> 0.85f; Quality.HIGH -> 1f; Quality.MAXIMUM -> 1.1f }
            val hasFaces = p.faces.isNotEmpty()
            val noiseDenoise = when (p.noise) { Level.HIGH -> 0.55f; Level.MEDIUM -> 0.28f; Level.LOW -> 0f }
            // When the source is very poor, be extra conservative with generated facial detail.
            val poorSource = if (p.blur == Level.HIGH && p.noise == Level.HIGH) 0.8f else 1f
            fun fs(base: Float) = (base * qf * poorSource).coerceIn(0f, 0.9f)

            val resolved = if (mode != EnhanceMode.AUTO) mode else when {
                p.isOldPhoto -> EnhanceMode.OLD_PHOTO
                p.isPortrait -> EnhanceMode.PORTRAIT
                p.noise == Level.HIGH || p.blur == Level.HIGH || p.hasJpegArtifacts -> EnhanceMode.LOW_QUALITY
                else -> EnhanceMode.AUTO
            }
            val needsTone = p.exposure != Exposure.NORMAL || p.contrast == Level.LOW
            return when (resolved) {
                EnhanceMode.OLD_PHOTO -> Recipe(
                    resolved, if (p.noise == Level.LOW) 0.35f else 0.6f, true, hasFaces, fs(0.7f), 1f,
                )
                EnhanceMode.PORTRAIT -> Recipe(resolved, noiseDenoise * 0.7f, false, hasFaces, fs(0.8f), 0.8f)
                EnhanceMode.LOW_QUALITY -> Recipe(
                    resolved, maxOf(noiseDenoise, if (p.hasJpegArtifacts) 0.3f else 0.2f), false, hasFaces, fs(0.65f), 0.8f,
                )
                EnhanceMode.HD_UPSCALE -> Recipe(resolved, 0f, false, false, 0f, 0f)
                EnhanceMode.AUTO -> Recipe(resolved, noiseDenoise, false, hasFaces, fs(0.7f), if (needsTone) 0.7f else 0.4f)
            }
        }
    }
}
