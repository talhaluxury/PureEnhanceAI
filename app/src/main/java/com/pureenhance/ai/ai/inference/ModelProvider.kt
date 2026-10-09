package com.pureenhance.ai.ai.inference

import ai.onnxruntime.OrtEnvironment
import android.content.Context
import android.os.Build
import com.pureenhance.ai.ai.models.ModelSpecs
import com.pureenhance.ai.utilities.DeviceProfile
import com.pureenhance.ai.utilities.DeviceTier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Lazy, cached model loading. Models are created once and reused until memory is needed elsewhere. */
class ModelProvider(private val context: Context) {
    private val env: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }
    private val store = ModelStore(context)
    private val prefs = context.getSharedPreferences("backend_health", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private var sr: OrtModel? = null
    private var face: OrtModel? = null

    /** Set by the pipeline while a job runs so [onTrimMemory] never closes a model in use. */
    @Volatile var busy: Boolean = false

    fun hasModel(file: String): Boolean = store.exists(file)

    suspend fun superRes(): OrtModel = mutex.withLock {
        sr ?: withContext(Dispatchers.Default) {
            load(ModelSpecs.SR_FILE, ModelSpecs.TILE, ModelSpecs.SR_SCALE)
        }.also { sr = it }
    }

    suspend fun faceModel(): OrtModel = mutex.withLock {
        face ?: withContext(Dispatchers.Default) {
            load(ModelSpecs.FACE_FILE, ModelSpecs.FACE_SIZE, 1)
        }.also { face = it }
    }

    suspend fun releaseFace() = mutex.withLock { face?.close(); face = null }

    fun onTrimMemory() {
        if (busy) return
        face?.close(); face = null
        sr?.close(); sr = null
    }

    private fun load(file: String, testSize: Int, outScale: Int): OrtModel {
        val device = DeviceProfile.read(context)
        val path = store.resolve(file).absolutePath
        val order = buildList {
            // NNAPI is skipped on purpose: many phones route it to a very slow reference CPU driver.
            if (!isBad(file, Backend.XNNPACK)) add(Backend.XNNPACK)
            add(Backend.CPU)
        }
        return OrtModel.open(
            env = env, name = file, path = path, order = order,
            threads = device.cores.coerceIn(2, 4),
            onRejected = { markBad(file, it) },
            selfTest = { selfTest(it, testSize, outScale) },
        )
    }

    private fun key(file: String, b: Backend) = "bad_${file}_$b"
    private fun isBad(file: String, b: Backend) = prefs.getBoolean(key(file, b), false)
    private fun markBad(file: String, b: Backend) {
        if (b != Backend.CPU) prefs.edit().putBoolean(key(file, b), true).apply()
    }

    /** Guards against drivers that "work" but return NaN/constant data (black output). */
    private fun selfTest(model: OrtModel, size: Int, outScale: Int): Boolean {
        val plane = size * size
        val input = FloatArray(3 * plane) { 0.1f + 0.8f * ((it % size).toFloat() / size) }
        val out = model.run(input, size, size)
        if (out.size != 3 * plane * outScale * outScale) return false
        var mn = Float.MAX_VALUE
        var mx = -Float.MAX_VALUE
        for (v in out) {
            if (v.isNaN() || v.isInfinite()) return false
            if (v < mn) mn = v
            if (v > mx) mx = v
        }
        return mx - mn > 1e-3f
    }
}
