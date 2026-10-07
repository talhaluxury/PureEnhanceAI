package com.pureenhance.ai.ai.face

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.pureenhance.ai.ai.FaceInfo
import kotlinx.coroutines.tasks.await

interface FaceDetecting {
    /** Face boxes in the coordinate space of [bitmap]. */
    suspend fun detect(bitmap: Bitmap): List<FaceInfo>
}

/** ML Kit on-device face detector (bundled model, Apache-2.0 SDK, works offline). */
class MlKitFaceDetector : FaceDetecting, AutoCloseable {
    private val detector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setMinFaceSize(0.03f)
                .build(),
        )
    }

    override suspend fun detect(bitmap: Bitmap): List<FaceInfo> =
        detector.process(InputImage.fromBitmap(bitmap, 0)).await().map {
            val b = it.boundingBox
            FaceInfo(b.left.coerceAtLeast(0), b.top.coerceAtLeast(0), b.right.coerceAtMost(bitmap.width), b.bottom.coerceAtMost(bitmap.height))
        }.filter { it.width > 8 && it.height > 8 }

    override fun close() = detector.close()
}
