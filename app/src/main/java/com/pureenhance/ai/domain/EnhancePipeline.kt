package com.pureenhance.ai.domain

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.pureenhance.ai.ai.ImageAnalyzer
import com.pureenhance.ai.ai.face.FaceRestorer
import com.pureenhance.ai.ai.inference.ModelProvider
import com.pureenhance.ai.ai.inference.SuperResInference
import com.pureenhance.ai.ai.models.ModelSpecs
import com.pureenhance.ai.ai.postprocessing.AutoLevels
import com.pureenhance.ai.ai.postprocessing.BilateralDenoiser
import com.pureenhance.ai.ai.postprocessing.DamageReducer
import com.pureenhance.ai.ai.postprocessing.Lanczos
import com.pureenhance.ai.ai.tiling.TiledUpscaler
import com.pureenhance.ai.image.BitmapUtils
import com.pureenhance.ai.image.ImageLoader
import com.pureenhance.ai.image.MemoryPlanner
import com.pureenhance.ai.utilities.DeviceProfile
import com.pureenhance.ai.utilities.EnhanceException
import com.pureenhance.ai.utilities.ThermalGuard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * ORIGINAL → ANALYSIS → (damage reduction, denoise) → FACE RESTORATION → TILED AI SUPER-RESOLUTION → refinement data.
 * Memory-safe: input is decoded at the planned size, output is built tile by tile, and an out-of-memory
 * failure automatically retries with a smaller budget before giving up.
 */
class EnhancePipeline(
    private val context: Context,
    private val loader: ImageLoader,
    private val models: ModelProvider,
    private val analyzer: ImageAnalyzer,
) {
    private val faceRestorer = FaceRestorer(models)

    /** All heavy work runs off the main thread (a ViewModel scope starts on Main, which caused ANRs). */
    suspend fun run(uri: Uri, requested: EnhanceSettings, onProgress: (Progress) -> Unit): EnhanceResult =
        withContext(Dispatchers.Default) { runWithRetry(uri, requested, onProgress) }

    private suspend fun runWithRetry(uri: Uri, requested: EnhanceSettings, onProgress: (Progress) -> Unit): EnhanceResult {
        var attempt = 0
        var divisor = 1.0
        var settings = requested
        while (true) {
            try {
                models.busy = true
                return runOnce(uri, settings, divisor, onProgress)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                val oom = t is OutOfMemoryError || t is EnhanceException.OutOfMemory
                if (!oom || attempt >= 2) {
                    throw if (t is OutOfMemoryError) EnhanceException.OutOfMemory(t) else t
                }
                attempt++
                divisor *= 2.0
                settings = settings.copy(scale = 2)
                System.gc()
            } finally {
                models.busy = false
            }
        }
    }

    private suspend fun runOnce(
        uri: Uri, settings: EnhanceSettings, divisor: Double, onProgress: (Progress) -> Unit,
    ): EnhanceResult {
        val notes = ArrayList<String>()
        fun report(stage: Stage, f: Float, eta: Int? = null) = onProgress(Progress(stage, f.coerceIn(0f, 1f), eta))

        ThermalGuard.throttleMs(context) // throws if the device is already critically hot
        report(Stage.ANALYZING, 0f)

        val device = DeviceProfile.read(context)
        var quality = settings.quality
        if (quality == Quality.MAXIMUM && !device.maximumQualityAllowed) {
            quality = Quality.HIGH
            notes += "Maximum quality needs more memory than this device has, so High quality was used."
        }
        val info = withContext(Dispatchers.IO) { loader.readInfo(uri) }
        val maxOut = (MemoryPlanner.maxOutputPixels(device.tier, device.availRamBytes, quality.memoryFactor) / divisor)
            .toLong().coerceAtLeast(500_000L)
        val memPlan = MemoryPlanner.plan(info.width, info.height, settings.scale, maxOut)
        val plan = MemoryPlanner.capInput(memPlan, quality.maxInputPixels)
        plan.message(settings.scale)?.let { notes += it }
        if (plan.inputWidth != memPlan.inputWidth) {
            val mp = String.format(java.util.Locale.US, "%.1f", plan.inputWidth.toLong() * plan.inputHeight / 1e6)
            notes += "To keep it fast, this photo was processed at $mp MP. Choose Maximum quality for more detail."
        }

        val input = withContext(Dispatchers.IO) { loader.decode(uri, info, plan.inputWidth, plan.inputHeight) }
        var work = input
        var base: Bitmap? = null
        try {
            val profile = analyzer.analyze(input)
            val recipe = Recipe.resolve(settings.mode, profile, quality)
            currentCoroutineContext().ensureActive()

            // ---- restoration: dust/scratches, then edge-preserving denoise ----
            report(Stage.RESTORING, 0.08f)
            val ctx = currentCoroutineContext()
            val check = { ctx.ensureActive() }
            if (recipe.damageReduction) work = swap(work, DamageReducer.apply(work, check = check), input)
            if (recipe.denoise > 0f) work = swap(work, BilateralDenoiser.apply(work, recipe.denoise, check), input)

            // ---- faces (on the cleaned input, before upscaling) ----
            var layers = emptyList<com.pureenhance.ai.ai.face.FaceLayer>()
            if (recipe.faces) {
                when {
                    profile.faces.isEmpty() ->
                        if (settings.mode == EnhanceMode.PORTRAIT) notes += "No faces were found, so the standard enhancement was used."
                    !models.hasModel(ModelSpecs.FACE_FILE) ->
                        notes += "The face model isn't installed in this build, so faces were enhanced with the standard model."
                    else -> {
                        report(Stage.FACES, 0.18f)
                        layers = try {
                            faceRestorer.restore(work, profile.faces, recipe.faceStrength) { f -> report(Stage.FACES, 0.18f + 0.14f * f) }
                        } catch (e: EnhanceException.ModelLoadFailed) {
                            notes += "Face restoration couldn't start on this device, so faces were enhanced with the standard model."
                            emptyList()
                        }
                        if (device.tier <= com.pureenhance.ai.utilities.DeviceTier.MID) models.releaseFace()
                    }
                }
            }
            if (layers.isNotEmpty()) notes += "${layers.size} face(s) restored conservatively to keep identity."

            // ---- tiled AI super-resolution ----
            report(Stage.UPSCALING, 0.34f)
            val fast = quality == Quality.BALANCED
            val upscaled: Bitmap = if (fast) {
                notes += "Fast mode: faces are restored with AI, the rest is cleanly upscaled. Choose High or Maximum for full AI detail (much slower)."
                Lanczos.resize(work, plan.scale, check)
            } else {
                val sr = models.superRes()
                notes += "AI engine: ${sr.backend}"
                val upscaler = TiledUpscaler(
                    SuperResInference(sr, ModelSpecs.SR_SCALE), ModelSpecs.TILE,
                    throttleMs = { ThermalGuard.throttleMs(context) },
                )
                val t0 = System.nanoTime()
                upscaler.upscale(work, plan.scale, quality.tilePad) { f ->
                    val eta = if (f > 0.03f) (((System.nanoTime() - t0) / 1e9) * (1 - f) / f).toInt() else null
                    report(Stage.UPSCALING, 0.34f + 0.56f * f, eta)
                }
            }
            base = upscaled

            // ---- finalize ----
            report(Stage.FINALIZING, 0.92f)
            val proxy = BitmapUtils.fitWithin(base, PREVIEW_MAX)
            val levels = AutoLevels.compute(proxy)
            val before = BitmapUtils.fitWithin(input, PREVIEW_MAX)
            val inputW = input.width
            if (work !== input) work.recycle()
            if (before !== input) input.recycle()
            report(Stage.FINALIZING, 1f)

            val result = EnhanceResult(
                base = base, proxy = proxy, before = before, faces = layers, inputWidth = inputW,
                levels = levels, original = info, profile = profile, modeUsed = recipe.modeUsed, notes = notes,
                defaultParams = EditParams(sharpness = if (fast) 0.3f else 0f),
            )
            base = null // ownership moved to the result
            return result
        } catch (t: Throwable) {
            if (work !== input && !work.isRecycled) work.recycle()
            if (!input.isRecycled) input.recycle()
            base?.recycle()
            throw t
        }
    }

    private fun swap(old: Bitmap, new: Bitmap, keep: Bitmap): Bitmap {
        if (new !== old && old !== keep) old.recycle()
        return new
    }

    companion object {
        const val PREVIEW_MAX = 2560
    }
}
