package com.pureenhance.ai.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Primary call-to-action with press feedback (scale) and a premium gradient. */
@Composable
fun GradientButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.97f else 1f, spring(stiffness = 500f), label = "press")
    val brush = Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))
    Box(
        modifier
            .scale(s)
            .alpha(if (enabled) 1f else 0.45f)
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(brush)
            .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SoftButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.97f else 1f, spring(stiffness = 500f), label = "press")
    Surface(
        modifier = modifier.scale(s).alpha(if (enabled) 1f else 0.45f).heightIn(min = 52.dp)
            .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Box(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** Translucent "glass" card. */
@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        content = content,
    )
}

/** Single-choice pill selector. Disabled options stay visible but cannot be selected. */
@Composable
fun Segmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: (Int) -> Boolean = { true },
    badges: Map<Int, String> = emptyMap(),
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            val enabled = isEnabled(i)
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable(enabled = enabled, role = Role.RadioButton) { onSelect(i) }
                    .semantics { if (!enabled) disabled(); contentDescription = label + (badges[i]?.let { ", $it" } ?: "") },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label + (badges[i]?.let { "  ·  $it" } ?: ""),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.4f),
                )
            }
        }
    }
}

/** Animated success check (stroke draws itself). */
@Composable
fun SuccessCheck(modifier: Modifier = Modifier.size(56.dp), color: Color = MaterialTheme.colorScheme.secondary) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(450, easing = FastOutSlowInEasing)) }
    Canvas(modifier) {
        val w = size.width
        val path = Path().apply {
            moveTo(w * 0.22f, w * 0.52f); lineTo(w * 0.43f, w * 0.72f); lineTo(w * 0.78f, w * 0.30f)
        }
        val measure = PathMeasure().apply { setPath(path, false) }
        val seg = Path()
        measure.getSegment(0f, measure.length * progress.value, seg, true)
        drawCircle(color.copy(alpha = 0.18f))
        drawPath(seg, color, style = Stroke(width = w * 0.08f, cap = StrokeCap.Round))
    }
}

fun sparklePath(cx: Float, cy: Float, r: Float): Path = Path().apply {
    moveTo(cx, cy - r)
    cubicTo(cx + r * 0.06f, cy - r * 0.35f, cx + r * 0.35f, cy - r * 0.06f, cx + r, cy)
    cubicTo(cx + r * 0.35f, cy + r * 0.06f, cx + r * 0.06f, cy + r * 0.35f, cx, cy + r)
    cubicTo(cx - r * 0.06f, cy + r * 0.35f, cx - r * 0.35f, cy + r * 0.06f, cx - r, cy)
    cubicTo(cx - r * 0.35f, cy - r * 0.06f, cx - r * 0.06f, cy - r * 0.35f, cx, cy - r)
    close()
}

@Suppress("unused")
private val keepOffsetImport = Offset.Zero
