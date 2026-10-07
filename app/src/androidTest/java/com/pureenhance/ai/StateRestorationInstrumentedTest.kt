package com.pureenhance.ai

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pureenhance.ai.domain.EnhanceMode
import com.pureenhance.ai.domain.Quality
import com.pureenhance.ai.presentation.MainViewModel
import com.pureenhance.ai.presentation.Screen
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StateRestorationInstrumentedTest {
    @Test fun settingsSurviveViewModelRecreation() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val handle = SavedStateHandle()
        val first = MainViewModel(app, handle)
        first.setMode(EnhanceMode.OLD_PHOTO); first.setQuality(Quality.BALANCED); first.setScale(4)
        // Simulates process death: a new ViewModel is built from the same saved state.
        val second = MainViewModel(app, handle)
        assertEquals(EnhanceMode.OLD_PHOTO, second.state.value.mode)
        assertEquals(Quality.BALANCED, second.state.value.quality)
        assertEquals(4, second.state.value.scale)
        assertEquals(Screen.HOME, second.state.value.screen)
    }

    @Test fun cancelReturnsToPreviewWithoutProgress() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = MainViewModel(app, SavedStateHandle())
        vm.cancelEnhance()
        assertEquals(Screen.PREVIEW, vm.state.value.screen)
        assertEquals(null, vm.state.value.progress)
    }
}
