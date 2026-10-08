package com.pureenhance.ai.presentation.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pureenhance.ai.domain.Progress
import com.pureenhance.ai.domain.Stage
import com.pureenhance.ai.presentation.components.SoftButton
import com.pureenhance.ai.presentation.components.sparklePath

@Composable
fun ProcessingScreen(progress: Progress?, onCancel: () -> Unit) {
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    val t = rememberInfiniteTransition(label = "ai")
    val rot by t.animateFloat(0f, 360f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "rot")
    val pulse by t.animateFloat(0.85f, 1.1f, infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val current = progress?.stage ?: Stage.ANALYZING
    val fraction = progress?.fraction ?: 0f
    val eta = progress?.etaSeconds
    val etaText = eta?.let { "  ·  about " + (if (it >= 90) "${it / 60 + 1} min" else "$it s") + " left" } ?: ""

    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val brush = Brush.sweepGradient(listOf(primary.copy(alpha = 0f), primary, secondary))
                rotate(rot) {
                    drawArc(brush, 0f, 270f, false, topLeft = Offset(8f, 8f), size = Size(size.width - 16f, size.height - 16f), style = Stroke(10f, cap = StrokeCap.Round))
                }
                rotate(-rot * 0.6f) {
                    drawArc(secondary.copy(alpha = 0.5f), 40f, 120f, false, topLeft = Offset(34f, 34f), size = Size(size.width - 68f, size.height - 68f), style = Stroke(6f, cap = StrokeCap.Round))
                }
                scale(pulse) { drawPath(sparklePath(size.width / 2, size.height / 2, size.width * 0.17f), Brush.linearGradient(listOf(primary, secondary))) }
            }
        }
        Spacer(Modifier.height(28.dp))
        Text("ENHANCING...", style = MaterialTheme.typography.labelLarge, letterSpacing = 4.sp, color = primary)
        Spacer(Modifier.height(20.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Stage.entries.forEach { s ->
                val done = s.ordinal < current.ordinal
                val active = s == current
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.alpha(if (done || active) 1f else 0.4f)) {
                    Text(if (done) "✓" else if (active) "●" else "○", color = if (done) secondary else primary, modifier = Modifier.width(28.dp))
                    Text(s.label, style = MaterialTheme.typography.bodyLarge, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().height(6.dp))
        Spacer(Modifier.height(8.dp))
        Text("Please wait...  ${(fraction * 100).toInt()}%$etaText", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        SoftButton("Cancel", onCancel)
    }
}
