package com.pureenhance.ai.presentation.components

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private class LeftClip(private val px: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rectangle(Rect(0f, 0f, px.coerceIn(0f, size.width), size.height))
}

/**
 * Before/after comparison. The divider lives in screen space; pinch to zoom, drag to pan,
 * double-tap toggles fit ↔ 2.5×. Both images are drawn with identical transforms so they stay aligned.
 */
@Composable
fun CompareSlider(before: ImageBitmap, after: ImageBitmap, modifier: Modifier = Modifier) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var frac by remember { mutableFloatStateOf(0.96f) }
    var dragged by remember { mutableStateOf(false) }

    // Reveal animation: the divider sweeps in from the edge to the middle.
    LaunchedEffect(Unit) {
        animate(0.96f, 0.5f, animationSpec = spring(dampingRatio = 0.7f, stiffness = 120f)) { v, _ -> if (!dragged) frac = v }
    }

    BoxWithConstraints(modifier.clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = 0.25f))) {
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()

        fun clampOffset(o: Offset, s: Float) = Offset(
            o.x.coerceIn(-(wPx * (s - 1f) / 2f), wPx * (s - 1f) / 2f),
            o.y.coerceIn(-(hPx * (s - 1f) / 2f), hPx * (s - 1f) / 2f),
        )

        val layer = Modifier.fillMaxSize().graphicsLayer {
            scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
        }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(wPx, hPx) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val ns = (scale * zoom).coerceIn(1f, 8f)
                        val center = Offset(wPx / 2f, hPx / 2f)
                        val newOffset = centroid + pan - center - (centroid - center - offset) * (ns / scale)
                        scale = ns
                        offset = clampOffset(newOffset, ns)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                    })
                },
        ) {
            Image(after, "Enhanced photo", layer, contentScale = ContentScale.Fit)
            Box(Modifier.fillMaxSize().clip(LeftClip(frac * wPx))) {
                Image(before, "Original photo", layer, contentScale = ContentScale.Fit)
            }
        }

        // divider
        Box(
            Modifier
                .fillMaxHeight()
                .width(2.dp)
                .offset { IntOffset((frac * wPx - 1.dp.toPx()).roundToInt(), 0) }
                .background(Color.White.copy(alpha = 0.9f)),
        )
        // draggable handle (48 dp touch target)
        Box(
            Modifier
                .fillMaxHeight()
                .width(48.dp)
                .offset { IntOffset((frac * wPx - 24.dp.toPx()).roundToInt(), 0) }
                .pointerInput(wPx) {
                    detectHorizontalDragGestures(onDragStart = { dragged = true }) { change, dx ->
                        change.consume()
                        frac = (frac + dx / wPx).coerceIn(0f, 1f)
                    }
                }
                .semantics {
                    contentDescription = "Before and after slider"
                    stateDescription = "${(frac * 100).roundToInt()} percent original"
                },
            contentAlignment = Alignment.Center,
        ) {
            Surface(shape = CircleShape, color = Color.White, shadowElevation = 6.dp, modifier = Modifier.size(38.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("◀▶", color = Color(0xFF14121F), style = MaterialTheme.typography.labelMedium) }
            }
        }

        Pill("BEFORE", Modifier.align(Alignment.TopStart).padding(12.dp))
        Pill("AFTER", Modifier.align(Alignment.TopEnd).padding(12.dp))
        if (scale > 1.01f) {
            Pill("Fit", Modifier.align(Alignment.BottomEnd).padding(12.dp), onClick = { scale = 1f; offset = Offset.Zero })
        }
    }
}

@Composable
private fun Pill(text: String, modifier: Modifier, onClick: (() -> Unit)? = null) {
    Surface(
        modifier = modifier.then(if (onClick != null) Modifier.pointerInput(Unit) { detectTapGestures { onClick() } } else Modifier),
        shape = RoundedCornerShape(50),
        color = Color.Black.copy(alpha = 0.55f),
    ) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}
