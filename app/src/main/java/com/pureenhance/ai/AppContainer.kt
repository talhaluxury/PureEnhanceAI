package com.pureenhance.ai

import android.content.Context
import com.pureenhance.ai.ai.ImageAnalyzer
import com.pureenhance.ai.ai.face.MlKitFaceDetector
import com.pureenhance.ai.ai.inference.ModelProvider
import com.pureenhance.ai.domain.EditRenderer
import com.pureenhance.ai.domain.EnhancePipeline
import com.pureenhance.ai.image.ImageLoader
import com.pureenhance.ai.storage.MediaStoreSaver
import com.pureenhance.ai.utilities.DeviceProfile

/** Manual dependency injection: one place that wires the whole app, easy to replace in tests. */
class AppContainer(val context: Context) {
    val loader = ImageLoader(context.contentResolver)
    val models = ModelProvider(context)
    private val faceDetector = MlKitFaceDetector()
    val analyzer = ImageAnalyzer(faceDetector)
    val pipeline = EnhancePipeline(context, loader, models, analyzer)
    val renderer = EditRenderer()
    val saver = MediaStoreSaver(context)
    fun device(): DeviceProfile = DeviceProfile.read(context)
}
