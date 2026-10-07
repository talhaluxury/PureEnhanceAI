package com.pureenhance.ai.ai.inference

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.util.Log
import com.pureenhance.ai.utilities.EnhanceException
import java.nio.FloatBuffer

/** Acceleration backends, tried in order. ONNX Runtime Android exposes GPU/NPU only through NNAPI. */
enum class Backend { NNAPI, XNNPACK, CPU }

/** Backend-agnostic tile inference so the engine can be swapped (e.g. NCNN) without touching the pipeline. */
interface TileInference {
    /** Output size divided by input size (4 for Real-ESRGAN x4, 1 for same-size models). */
    val scale: Int

    /** [chw] is a 3×size×size planar float tensor; returns 3×(size·scale)×(size·scale). */
    fun infer(chw: FloatArray, size: Int): FloatArray
}

class OrtModel private constructor(
    val name: String,
    private val env: OrtEnvironment,
    private val session: OrtSession,
    val backend: Backend,
) : AutoCloseable {

    private val inputName: String = session.inputNames.first()

    fun run(input: FloatArray, height: Int, width: Int): FloatArray {
        val tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(input), longArrayOf(1, 3, height.toLong(), width.toLong()))
        try {
            session.run(mapOf(inputName to tensor)).use { result ->
                val out = result.get(0) as OnnxTensor
                val fb = out.floatBuffer
                val arr = FloatArray(fb.remaining())
                fb.get(arr)
                return arr
            }
        } finally {
            tensor.close()
        }
    }

    override fun close() {
        runCatching { session.close() }
    }

    companion object {
        private const val TAG = "OrtModel"

        /**
         * Opens [path] with the first backend in [order] that both loads and passes [selfTest].
         * Drivers that load but produce NaN/constant output (black images) are rejected and the
         * next backend is tried. [onRejected] lets the caller remember bad backends.
         */
        fun open(
            env: OrtEnvironment,
            name: String,
            path: String,
            order: List<Backend>,
            threads: Int,
            onRejected: (Backend) -> Unit,
            selfTest: (OrtModel) -> Boolean,
        ): OrtModel {
            var last: Throwable? = null
            for (backend in order) {
                var opts: OrtSession.SessionOptions? = null
                try {
                    opts = OrtSession.SessionOptions()
                    opts.setIntraOpNumThreads(threads)
                    opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    when (backend) {
                        Backend.NNAPI -> opts.addNnapi()
                        Backend.XNNPACK -> opts.addXnnpack(mapOf("intra_op_num_threads" to threads.toString()))
                        Backend.CPU -> Unit
                    }
                    val model = OrtModel(name, env, env.createSession(path, opts), backend)
                    if (selfTest(model)) {
                        Log.i(TAG, "$name running on $backend")
                        return model
                    }
                    model.close()
                    onRejected(backend)
                    Log.w(TAG, "$name: $backend produced invalid output, falling back")
                } catch (t: Throwable) {
                    if (t is OutOfMemoryError) throw EnhanceException.OutOfMemory(t)
                    last = t
                    onRejected(backend)
                    Log.w(TAG, "$name: $backend unavailable (${t.message})")
                } finally {
                    runCatching { opts?.close() }
                }
            }
            throw EnhanceException.ModelLoadFailed(name, last)
        }
    }
}

/** Adapts [OrtModel] to [TileInference] for the x4 super-resolution network. */
class SuperResInference(private val model: OrtModel, override val scale: Int) : TileInference {
    override fun infer(chw: FloatArray, size: Int): FloatArray = try {
        model.run(chw, size, size)
    } catch (e: OutOfMemoryError) {
        throw EnhanceException.OutOfMemory(e)
    } catch (e: Exception) {
        throw EnhanceException.InferenceFailed(e)
    }
}
