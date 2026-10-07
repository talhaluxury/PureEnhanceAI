package com.pureenhance.ai.presentation

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pureenhance.ai.presentation.screens.HomeScreen
import com.pureenhance.ai.presentation.screens.PreviewScreen
import com.pureenhance.ai.presentation.screens.PrivacyScreen
import com.pureenhance.ai.presentation.screens.ProcessingScreen
import com.pureenhance.ai.presentation.screens.ResultScreen
import android.content.ClipData

@Composable
fun PureEnhanceRoot(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                is UiEvent.Share -> {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "image/jpeg"
                        putExtra(Intent.EXTRA_STREAM, e.uri)
                        clipData = ClipData.newRawUri(null, e.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(send, "Share enhanced photo"))
                }
                is UiEvent.View -> context.startActivity(
                    Intent(Intent.ACTION_VIEW).setDataAndType(e.uri, "image/jpeg").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                )
            }
        }
    }

    BackHandler(enabled = state.screen != Screen.HOME) { vm.back() }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AnimatedContent(
            targetState = state.screen,
            transitionSpec = { (fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 24 }) togetherWith fadeOut(tween(160)) },
            label = "screen",
        ) { screen ->
            when (screen) {
                Screen.HOME -> HomeScreen(state, vm::onPhotoSelected, vm::openPrivacy, { vm.view(it.uri) }, vm::dismissError)
                Screen.PREVIEW -> PreviewScreen(state, vm::back, vm::setMode, vm::setQuality, vm::setScale, vm::startEnhance, vm::dismissError)
                Screen.PROCESSING -> ProcessingScreen(state.progress, vm::cancelEnhance)
                Screen.RESULT -> ResultScreen(state, vm::back, vm::onParamsChange, vm::save, vm::share, vm::goHome)
                Screen.PRIVACY -> PrivacyScreen(vm::back)
            }
        }
    }
}
