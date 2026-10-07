package com.pureenhance.ai.presentation.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pureenhance.ai.presentation.UiState
import com.pureenhance.ai.presentation.components.GlassCard
import com.pureenhance.ai.presentation.components.GradientButton
import com.pureenhance.ai.presentation.components.sparklePath
import com.pureenhance.ai.storage.RecentItem

@Composable
fun HomeScreen(
    state: UiState,
    onPick: (Uri) -> Unit,
    onPrivacy: () -> Unit,
    onOpenRecent: (RecentItem) -> Unit,
    onDismissError: () -> Unit,
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(onPick) }
    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(28.dp))
        Text("PUREENHANCE AI", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, letterSpacing = 4.sp)
        Spacer(Modifier.height(14.dp))
        Text("Restore your photos\nwith on-device AI", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(10.dp))
        Text(
            "Free forever. No ads, no account, no watermark.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Hero()
        Spacer(Modifier.height(24.dp))
        GradientButton("+  Choose Photo", onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))
        GlassCard(Modifier.fillMaxWidth().clickable(onClick = onPrivacy)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Private by design", style = MaterialTheme.typography.titleMedium)
                    Text("Photos never leave your phone. Tap to learn how.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("›", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        state.error?.let {
            Spacer(Modifier.height(14.dp))
            GlassCard(Modifier.fillMaxWidth().clickable(onClick = onDismissError)) {
                Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(28.dp))
        Text("Recent Results", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        if (state.recents.isEmpty()) {
            Text("Your enhanced photos will appear here.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.recents, key = { it.uri.toString() }) { item ->
                    Box(
                        Modifier.size(112.dp).clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onOpenRecent(item) }
                            .semantics { contentDescription = "Open enhanced photo" },
                    ) {
                        item.thumbnail?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Hero() {
    val t = rememberInfiniteTransition(label = "hero")
    val rot by t.animateFloat(0f, 360f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart), label = "rot")
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    Box(
        Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(primary.copy(alpha = 0.25f), secondary.copy(alpha = 0.18f)))),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(120.dp).graphicsLayer { rotationZ = rot }) {
            drawPath(sparklePath(size.width / 2, size.height / 2, size.width / 2), Brush.linearGradient(listOf(primary, secondary)))
        }
    }
}
