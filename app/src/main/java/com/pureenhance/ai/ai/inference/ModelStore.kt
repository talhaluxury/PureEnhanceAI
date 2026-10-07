package com.pureenhance.ai.ai.inference

import android.content.Context
import com.pureenhance.ai.utilities.EnhanceException
import java.io.File
import java.io.IOException

/**
 * Resolves a model file name to a real file path (ONNX Runtime opens by path).
 * Search order: filesDir/models (side-loaded) → bundled asset (copied once to noBackupFilesDir).
 */
class ModelStore(private val context: Context) {

    private fun assetPath(name: String) = "models/$name"

    fun exists(name: String): Boolean =
        File(context.filesDir, "models/$name").exists() ||
            runCatching { context.assets.open(assetPath(name)).close(); true }.getOrDefault(false)

    fun resolve(name: String): File {
        val sideLoaded = File(context.filesDir, "models/$name")
        if (sideLoaded.exists() && sideLoaded.length() > 0) return sideLoaded

        val exists = runCatching { context.assets.open(assetPath(name)).close(); true }.getOrDefault(false)
        if (!exists) throw EnhanceException.ModelMissing(name)

        val assetLen = runCatching { context.assets.openFd(assetPath(name)).use { it.length } }.getOrNull()
        val cached = File(context.noBackupFilesDir, "models/$name")
        if (cached.exists() && cached.length() > 0 && (assetLen == null || cached.length() == assetLen)) return cached

        try {
            cached.parentFile?.mkdirs()
            val tmp = File(cached.parentFile, "$name.tmp")
            context.assets.open(assetPath(name)).use { input -> tmp.outputStream().use { input.copyTo(it, 1 shl 20) } }
            if (cached.exists()) cached.delete()
            if (!tmp.renameTo(cached)) throw IOException("rename failed")
        } catch (e: IOException) {
            throw EnhanceException.ModelLoadFailed(name, e)
        }
        return cached
    }
}
