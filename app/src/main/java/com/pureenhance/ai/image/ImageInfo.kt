package com.pureenhance.ai.image

/** Dimensions are *oriented* (EXIF rotation already applied). */
data class ImageInfo(
    val width: Int,
    val height: Int,
    val rawWidth: Int,
    val rawHeight: Int,
    val exifOrientation: Int,
    val mimeType: String?,
) {
    val megapixels: Double get() = width.toLong() * height / 1_000_000.0
    val longSide: Int get() = maxOf(width, height)
}
