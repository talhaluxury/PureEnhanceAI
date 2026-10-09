package com.pureenhance.ai.presentation.screens

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.pureenhance.ai.domain.EnhanceMode
import com.pureenhance.ai.domain.Quality
import com.pureenhance.ai.presentation.UiState
import com.pureenhance.ai.presentation.components.GlassCard
import com.pureenhance.ai.presentation.components.GradientButton
import com.pureenhance.ai.presentation.components.Segmented
import java.util.Locale

@Composable
fun PreviewScreen(
    state: UiState,
    onBack: () -> Unit,
    onMode: (EnhanceMode) -> Unit,
    onQuality: (Quality) -> Unit,
    onScale: (Int) -> Unit,
    onEnhance: () -> Unit,
    onDismissError: () -> Unit,
) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val maxAllowed = state.device?.maximumQualityAllowed ?: false

    val image: @Composable (Modifier) -> Unit = { m ->
        GlassCard(m) {
            state.selectedPreview?.let {
                Image(it.asImageBitmap(), "Selected photo preview", Modifier.fillMaxSize().padding(6.dp).clip(RoundedCornerShape(18.dp)), contentScale = ContentScale.Fit)
            }
        }
    }
    val controls: @Composable (Modifier) -> Unit = { m ->
        Column(m.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.selectedInfo?.let {
                Text(
                    "${it.width} × ${it.height} px  ·  ${String.format(Locale.US, "%.1f", it.megapixels)} MP",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("AI ${state.mode.label} Enhance", style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(EnhanceMode.entries) { m2 ->
                    FilterChip(
                        selected = m2 == state.mode, onClick = { onMode(m2) },
                        label = { Text(m2.label) }, modifier = Modifier.height(48.dp),
                    )
                }
            }
            Text(state.mode.hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Text("Quality", style = MaterialTheme.typography.labelLarge)
            val q = Quality.entries
            Segmented(
                options = q.map { it.label }, selected = q.indexOf(state.quality),
                onSelect = { onQuality(q[it]) },
                isEnabled = { q[it] != Quality.MAXIMUM || maxAllowed },
                badges = mapOf(q.indexOf(state.recommendedQuality) to "★"),
            )
            if (!maxAllowed) {
                Text("Maximum needs a device with about 6 GB of RAM or more.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Text(
                when (state.quality) {
                    Quality.BALANCED -> "Fast: AI face restoration + clean upscale. Usually under a minute."
                    Quality.HIGH -> "High: full AI upscaling on a smaller image. Takes several minutes."
                    Quality.MAXIMUM -> "Maximum: full AI detail. Can take 10+ minutes."
                },
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Scale", style = MaterialTheme.typography.labelLarge)
            Segmented(
                options = listOf("2×", "4×"), selected = if (state.scale == 4) 1 else 0,
                onSelect = { onScale(if (it == 1) 4 else 2) },
                badges = mapOf((if (state.recommendedScale == 4) 1 else 0) to "Recommended"),
            )
            state.error?.let {
                GlassCard(Modifier.fillMaxWidth().clickable(onClick = onDismissError)) {
                    Text(it, Modifier.padding(14.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
            GradientButton("✨  ENHANCE", onClick = onEnhance, modifier = Modifier.fillMaxWidth())
        }
    }

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "‹  Back", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.height(48.dp).clickable(onClick = onBack).padding(end = 16.dp),
            )
            Spacer(Modifier.weight(1f))
            Text("Enhance Photo", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(64.dp))
        }
        if (landscape) {
            Row(Modifier.fillMaxSize().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                image(Modifier.weight(1.1f).fillMaxHeight())
                controls(Modifier.weight(0.9f).fillMaxHeight())
            }
        } else {
            image(Modifier.weight(1f).fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            controls(Modifier.fillMaxWidth().padding(bottom = 12.dp))
        }
    }
}

