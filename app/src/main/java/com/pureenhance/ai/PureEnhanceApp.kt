package com.pureenhance.ai

import android.app.Application
import android.content.ComponentCallbacks2
import java.io.File

class PureEnhanceApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Privacy: nothing is ever written to cache by the app, but make sure no stale temp data can exist.
        runCatching { File(cacheDir, "tmp").deleteRecursively() }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) container.models.onTrimMemory()
    }
}
