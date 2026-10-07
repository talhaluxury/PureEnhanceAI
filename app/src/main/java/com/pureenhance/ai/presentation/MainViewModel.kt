package com.pureenhance.ai.presentation

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.pureenhance.ai.PureEnhanceApp
import com.pureenhance.ai.domain.EditParams
import com.pureenhance.ai.domain.EnhanceMode
import com.pureenhance.ai.domain.EnhanceSettings
import com.pureenhance.ai.domain.Quality
import com.pureenhance.ai.image.MemoryPlanner
import com.pureenhance.ai.storage.RecentResults
import com.pureenhance.ai.utilities.DeviceTier
import com.pureenhance.ai.utilities.EnhanceException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(FlowPreview::class)
class MainViewModel(app: Application, private val saved: SavedStateHandle) : AndroidViewModel(app) {
    private val container = (app as PureEnhanceApp).container
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    private val edits = MutableStateFlow(EditParams())
    private var enhanceJob: Job? = null
    private var saveJob: Job? = null
    private var loadJob: Job? = null

    init {
        val device = container.device()
        // State restoration: selection and settings survive process death.
        val mode = saved.get<String>(KEY_MODE)?.let { runCatching { EnhanceMode.valueOf(it) }.getOrNull() } ?: EnhanceMode.AUTO
        val quality = saved.get<String>(KEY_QUALITY)?.let { runCatching { Quality.valueOf(it) }.getOrNull() } ?: defaultQuality(device.tier)
        _state.update { it.copy(device = device, mode = mode, quality = quality, scale = saved.get<Int>(KEY_SCALE) ?: 0) }
        saved.get<String>(KEY_URI)?.let { onPhotoSelected(Uri.parse(it)) }
        refreshRecents()
        viewModelScope.launch { edits.debounce(60).collectLatest { rerender(it) } }
    }

    // ------------------------------------------------------------------ selection
    fun onPhotoSelected(uri: Uri) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                val (info, preview) = withContext(Dispatchers.IO) {
                    val info = container.loader.readInfo(uri)
                    val s = minOf(1f, PREVIEW_SIDE.toFloat() / info.longSide)
                    info to container.loader.decode(uri, info, (info.width * s).toInt().coerceAtLeast(1), (info.height * s).toInt().coerceAtLeast(1))
                }
                val device = container.device()
                val quality = _state.value.quality.let { if (it == Quality.MAXIMUM && !device.maximumQualityAllowed) Quality.HIGH else it }
                val recommended = recommendedScale(info.width, info.height, device.tier, quality)
                saved[KEY_URI] = uri.toString()
                discardResult()
                _state.update {
                    it.copy(
                        screen = Screen.PREVIEW, selectedUri = uri, selectedInfo = info, selectedPreview = preview,
                        device = device, quality = quality, recommendedScale = recommended,
                        scale = if (it.scale == 2 || it.scale == 4) it.scale else recommended,
                        recommendedQuality = defaultQuality(device.tier), error = null,
                        saveState = SaveState.Idle,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: EnhanceException) {
                saved.remove<String>(KEY_URI)
                _state.update { it.copy(screen = Screen.HOME, error = e.userMessage) }
            } catch (e: Throwable) {
                saved.remove<String>(KEY_URI)
                _state.update { it.copy(screen = Screen.HOME, error = EnhanceException.UnsupportedImage(e).userMessage) }
            }
        }
    }

    fun setMode(m: EnhanceMode) { saved[KEY_MODE] = m.name; _state.update { it.copy(mode = m) } }
    fun setQuality(q: Quality) { saved[KEY_QUALITY] = q.name; _state.update { it.copy(quality = q) } }
    fun setScale(s: Int) { saved[KEY_SCALE] = s; _state.update { it.copy(scale = s) } }
    fun dismissError() = _state.update { it.copy(error = null) }

    // ------------------------------------------------------------------ enhance / cancel
    fun startEnhance() {
        val s = _state.value
        val uri = s.selectedUri ?: return
        if (s.screen == Screen.PROCESSING) return
        _state.update { it.copy(screen = Screen.PROCESSING, progress = null, error = null) }
        enhanceJob = viewModelScope.launch {
            try {
                val result = container.pipeline.run(uri, EnhanceSettings(s.mode, s.quality, s.scale)) { p ->
                    _state.update { it.copy(progress = p) }
                }
                discardResult()
                val first = withContext(Dispatchers.Default) { container.renderer.renderPreview(result, EditParams()) }
                edits.value = EditParams()
                _state.update {
                    it.copy(
                        screen = Screen.RESULT, result = result, params = EditParams(), previewAfter = first,
                        saveState = SaveState.Idle, progress = null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: EnhanceException) {
                _state.update { it.copy(screen = Screen.PREVIEW, progress = null, error = e.userMessage) }
            } catch (e: Throwable) {
                _state.update { it.copy(screen = Screen.PREVIEW, progress = null, error = EnhanceException.InferenceFailed(e).userMessage) }
            }
        }
    }

    /** Stops inference between tiles, frees temporary bitmaps (the pipeline recycles them) and returns to preview. */
    fun cancelEnhance() {
        enhanceJob?.cancel()
        enhanceJob = null
        _state.update { it.copy(screen = Screen.PREVIEW, progress = null) }
    }

    // ------------------------------------------------------------------ editing
    fun onParamsChange(p: EditParams) {
        _state.update { it.copy(params = p, saveState = SaveState.Idle) }
        edits.value = p
    }

    private suspend fun rerender(p: EditParams) {
        val r = _state.value.result ?: return
        try {
            val bmp = withContext(Dispatchers.Default) { container.renderer.renderPreview(r, p) }
            _state.update { if (it.result === r) it.copy(previewAfter = bmp) else it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Result may have been released while rendering; ignore.
        }
    }

    // ------------------------------------------------------------------ save / share
    fun save() { ensureSaved { } }

    fun share() = ensureSaved { uri -> _events.tryEmit(UiEvent.Share(uri)) }

    fun view(uri: Uri) { _events.tryEmit(UiEvent.View(uri)) }

    private fun ensureSaved(then: (Uri) -> Unit) {
        val s = _state.value
        val result = s.result ?: return
        (s.saveState as? SaveState.Saved)?.let { then(it.uri); return }
        if (s.saveState is SaveState.Saving) return
        _state.update { it.copy(saveState = SaveState.Saving) }
        saveJob = viewModelScope.launch {
            try {
                val uri = withContext(Dispatchers.Default) {
                    val full = container.renderer.renderFull(result, s.params)
                    try {
                        container.saver.save(full, s.selectedUri)
                    } finally {
                        full.recycle()
                    }
                }
                _state.update { it.copy(saveState = SaveState.Saved(uri)) }
                refreshRecents()
                then(uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: EnhanceException) {
                _state.update { it.copy(saveState = SaveState.Failed(e.userMessage)) }
            } catch (e: Throwable) {
                _state.update { it.copy(saveState = SaveState.Failed(EnhanceException.StorageFailed(e).userMessage)) }
            }
        }
    }

    // ------------------------------------------------------------------ navigation
    fun openPrivacy() = _state.update { it.copy(screen = Screen.PRIVACY) }

    fun goHome() {
        loadJob?.cancel()
        enhanceJob?.cancel()
        discardResult()
        saved.remove<String>(KEY_URI)
        _state.update {
            it.copy(screen = Screen.HOME, selectedUri = null, selectedInfo = null, selectedPreview = null, progress = null, saveState = SaveState.Idle)
        }
        refreshRecents()
    }

    fun back() {
        when (_state.value.screen) {
            Screen.PROCESSING -> cancelEnhance()
            Screen.PREVIEW, Screen.RESULT, Screen.PRIVACY -> goHome()
            Screen.HOME -> Unit
        }
    }

    private fun discardResult() {
        val r = _state.value.result ?: return
        _state.update { it.copy(result = null, previewAfter = null) }
        r.release()
    }

    fun refreshRecents() {
        viewModelScope.launch {
            val items = RecentResults.load(getApplication())
            _state.update { it.copy(recents = items) }
        }
    }

    override fun onCleared() {
        discardResult()
    }

    companion object {
        const val KEY_URI = "uri"
        const val KEY_MODE = "mode"
        const val KEY_QUALITY = "quality"
        const val KEY_SCALE = "scale"
        private const val PREVIEW_SIDE = 1600

        fun defaultQuality(tier: DeviceTier) = when (tier) {
            DeviceTier.LOW -> Quality.BALANCED
            DeviceTier.MID -> Quality.HIGH
            DeviceTier.HIGH, DeviceTier.ULTRA -> Quality.MAXIMUM
        }

        /** Recommends 4× only for small photos that also fit the device's memory budget. */
        fun recommendedScale(w: Int, h: Int, tier: DeviceTier, quality: Quality): Int {
            val want = MemoryPlanner.recommendedScale(maxOf(w, h))
            val maxOut = MemoryPlanner.maxOutputPixels(tier, Long.MAX_VALUE / 4, quality.memoryFactor)
            return MemoryPlanner.plan(w, h, want, maxOut).scale
        }
    }
}
