package com.pureenhance.ai.domain

import android.graphics.Bitmap
import com.pureenhance.ai.ai.face.FaceCompositor
import com.pureenhance.ai.ai.postprocessing.BilateralDenoiser
import com.pureenhance.ai.ai.postprocessing.Sharpener
import com.pureenhance.ai.ai.postprocessing.ToneMapper
import com.pureenhance.ai.ai.postprocessing.ToneParams
import com.pureenhance.ai.utilities.EnhanceException

/** Applies [EditParams] to a result. Used on the ≤2560 px proxy for live preview and on the full image when saving. */
class EditRenderer {
    fun renderPreview(r: EnhanceResult, p: EditParams): Bitmap = render(r.proxy, r, p)
    fun renderFull(r: EnhanceResult, p: EditParams): Bitmap = render(r.base, r, p)

    private fun render(source: Bitmap, r: EnhanceResult, p: EditParams): Bitmap {
        var bmp = try {
            source.copy(Bitmap.Config.ARGB_8888, true)
        } catch (e: OutOfMemoryError) {
            throw EnhanceException.OutOfMemory(e)
        } ?: throw EnhanceException.OutOfMemory()
        FaceCompositor.compose(bmp, r.faces, r.inputWidth, p.faceEnhance)
        bmp = replace(bmp, BilateralDenoiser.apply(bmp, p.denoise * 0.8f))
        bmp = replace(bmp, Sharpener.apply(bmp, p.sharpness))
        ToneMapper.applyInPlace(
            bmp,
            ToneParams(r.levels, p.autoEnhance, p.exposure, p.contrast, p.color, p.warmth),
        )
        return bmp
    }

    private fun replace(old: Bitmap, new: Bitmap): Bitmap {
        if (new !== old) old.recycle()
        return new
    }
}
