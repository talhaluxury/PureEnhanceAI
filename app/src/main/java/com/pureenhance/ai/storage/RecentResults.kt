package com.pureenhance.ai.storage

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RecentItem(val uri: Uri, val thumbnail: Bitmap?)

/** Lists the app's own saved results (files this app created are readable without any permission). */
object RecentResults {
    suspend fun load(context: Context, limit: Int = 12): List<RecentItem> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val items = ArrayList<RecentItem>()
        runCatching {
            resolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID),
                "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?",
                arrayOf("${Environment.DIRECTORY_PICTURES}/${MediaStoreSaver.ALBUM}/%"),
                "${MediaStore.Images.Media.DATE_ADDED} DESC",
            )?.use { c ->
                while (c.moveToNext() && items.size < limit) {
                    val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(0).toString())
                    val thumb = runCatching { resolver.loadThumbnail(uri, Size(320, 320), null) }.getOrNull()
                    items += RecentItem(uri, thumb)
                }
            }
        }
        items
    }
}

