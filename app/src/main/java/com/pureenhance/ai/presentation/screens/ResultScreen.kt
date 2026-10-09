package com.pureenhance.ai.presentation.screens

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.pureenhance.ai.domain.EditParams
import com.pureenhance.ai.presentation.SaveState
import com.pureenhance.ai.presentation.UiState
import com.pureenhance.ai.presentation.components.CompareSlider
import com.pureenhance.ai.presentation.components.GlassCard
import com.pureenhance.ai.presentation.components.GradientButton
import com.pureenhance.ai.presentation.components.SoftButton
import com.pureenhance.ai.presentation.components.SuccessCheck

@Composable
fun ResultScreen(
    state: UiState,
    onBack: () -> Unit,
    onParams: (EditParams) -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onAnother: () -> Unit,
) {
    val result = state.result ?: return
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var adjust by remember { mutableStateOf(false) }

    val compare: @Composable (Modifier) -> Unit = { m ->
        Box(m, contentAlignment = Alignment.Center) {
            val after = state.previewAfter
            if (after == null) CircularProgressIndicator() else CompareSlider(result.before.asImageBitmap(), after.asImageBitmap(), Modifier.fillMaxSize())
        }
    }
    val panel: @Composable (Modifier) -> Unit = { m ->
        Column(m.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Original ${result.original.width}×${result.original.height}  →  Enhanced ${result.outputWidth}×${result.outputHeight}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            result.notes.forEach {
                Text("• $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                if (adjust) "Hide fine-tuning  ▴" else "Fine-tune  ▾",
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.heightIn(min = 48.dp).clickable { adjust = !adjust },
            )
            AnimatedVisibility(adjust) { AdjustPanel(state.params, result.defaultParams, onParams) }

            when (val s = state.saveState) {
                is SaveState.Saved -> Row(verticalAlignment = Alignment.CenterVertically) {
                    SuccessCheck(Modifier.height(40.dp).width(40.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Saved to Pictures/PureEnhance", style = MaterialTheme.typography.bodyMedium)
                }
                is SaveState.Failed -> Text(s.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                else -> Unit
            }
            GradientButton(
                if (state.saveState is SaveState.Saving) "Saving…" else "SAVE HD PHOTO",
                onSave, Modifier.fillMaxWidth(), enabled = state.saveState !is SaveState.Saving && state.saveState !is SaveState.Saved,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SoftButton("SHARE", onShare, Modifier.weight(1f), enabled = state.saveState !is SaveState.Saving)
                SoftButton("ENHANCE ANOTHER", onAnother, Modifier.weight(1.4f))
            }
        }
    }

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹  Home", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.height(48.dp).clickable(onClick = onBack).padding(end = 16.dp))
            Spacer(Modifier.weight(1f))
            Text("Result", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(64.dp))
        }
        if (landscape) {
            Row(Modifier.fillMaxSize().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                compare(Modifier.weight(1.2f).fillMaxHeight())
                panel(Modifier.weight(0.8f).fillMaxHeight())
            }
        } else {
            compare(Modifier.weight(1f).fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            panel(Modifier.fillMaxWidth().heightIn(max = 330.dp).padding(bottom = 12.dp))
        }
    }
}

@Composable
private fun AdjustPanel(p: EditParams, defaults: EditParams, onChange: (EditParams) -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = p == EditParams.Original, onClick = { onChange(EditParams.Original) }, label = { Text("Original") }, modifier = Modifier.height(48.dp))
                FilterChip(selected = p == defaults, onClick = { onChange(defaults) }, label = { Text("Enhanced") }, modifier = Modifier.height(48.dp))
            }
            Control("Auto Enhance", p.autoEnhance, 0f..1.2f) { onChange(p.copy(autoEnhance = it)) }
            Control("Face Enhance", p.faceEnhance, 0f..1.3f) { onChange(p.copy(faceEnhance = it)) }
            Control("Sharpness", p.sharpness, 0f..1f) { onChange(p.copy(sharpness = it)) }
            Control("Denoise", p.denoise, 0f..1f) { onChange(p.copy(denoise = it)) }
            Control("Exposure", p.exposure, -1f..1f) { onChange(p.copy(exposure = it)) }
            Control("Contrast", p.contrast, -1f..1f) { onChange(p.copy(contrast = it)) }
            Control("Color", p.color, -1f..1f) { onChange(p.copy(color = it)) }
            Control("Warmth", p.warmth, -1f..1f) { onChange(p.copy(warmth = it)) }
        }
    }
}

@Composable
private fun Control(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.padding(top = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.heightIn(min = 48.dp))
    }
}
