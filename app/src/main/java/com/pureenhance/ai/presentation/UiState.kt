package com.pureenhance.ai.presentation

import android.graphics.Bitmap
import android.net.Uri
import com.pureenhance.ai.domain.EditParams
import com.pureenhance.ai.domain.EnhanceMode
import com.pureenhance.ai.domain.EnhanceResult
import com.pureenhance.ai.domain.Progress
import com.pureenhance.ai.domain.Quality
import com.pureenhance.ai.image.ImageInfo
import com.pureenhance.ai.storage.RecentItem
import com.pureenhance.ai.utilities.DeviceProfile

enum class Screen { HOME, PREVIEW, PROCESSING, RESULT, PRIVACY }

sealed interface SaveState {
    data object Idle : SaveState
    data object Saving : SaveState
    data class Saved(val uri: Uri) : SaveState
    data class Failed(val message: String) : SaveState
}

data class UiState(
    val screen: Screen = Screen.HOME,
    val selectedUri: Uri? = null,
    val selectedInfo: ImageInfo? = null,
    val selectedPreview: Bitmap? = null,
    val mode: EnhanceMode = EnhanceMode.AUTO,
    val quality: Quality = Quality.HIGH,
    val scale: Int = 2,
    val recommendedScale: Int = 2,
    val recommendedQuality: Quality = Quality.HIGH,
    val device: DeviceProfile? = null,
    val progress: Progress? = null,
    val result: EnhanceResult? = null,
    val params: EditParams = EditParams(),
    val previewAfter: Bitmap? = null,
    val saveState: SaveState = SaveState.Idle,
    val error: String? = null,
    val recents: List<RecentItem> = emptyList(),
)

sealed interface UiEvent {
    data class Share(val uri: Uri) : UiEvent
    data class View(val uri: Uri) : UiEvent
}
