package com.dinnerplan.chidian.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.dinnerplan.chidian.ui.theme.ChiDianColors
import com.dinnerplan.chidian.ui.theme.ChiDianGradients

fun staggerDelayMillis(index: Int): Int = index.coerceIn(0, 4) * 55

@Composable
fun Modifier.pressScale(onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    return scale(if (pressed) 0.97f else 1f)
        .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
}

@Composable
fun StaggeredVisible(index: Int, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(220, delayMillis = staggerDelayMillis(index))) +
            slideInVertically(
                animationSpec = tween(260, delayMillis = staggerDelayMillis(index), easing = FastOutSlowInEasing),
                initialOffsetY = { it / 5 }
            ),
        exit = fadeOut(animationSpec = tween(120))
    ) {
        content()
    }
}

@Composable
fun ShimmerLine(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val offset by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "shimmerOffset"
    )
    Box(
        modifier
            .height(5.dp)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        ChiDianColors.ActionPrimary.copy(alpha = 0.18f),
                        ChiDianColors.ActionPrimarySoft,
                        ChiDianColors.LocationAccentSoft
                    ),
                    start = Offset(offset * 600f, 0f),
                    end = Offset((offset + 1f) * 600f, 0f)
                )
            )
    )
}

@Composable
fun RadarPulse(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val transition = rememberInfiniteTransition(label = "radar")
    val pulse by transition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1.28f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "radarPulse"
    )
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scale(pulse)
                .alpha((1.3f - pulse).coerceIn(0f, 0.45f))
                .background(ChiDianGradients.NearbyHero, CircleShape)
        )
        content()
    }
}

@Composable
fun FloatingGlow(modifier: Modifier = Modifier, color: Color = ChiDianColors.Sun) {
    val transition = rememberInfiniteTransition(label = "floatingGlow")
    val drift by transition.animateFloat(
        initialValue = -14f,
        targetValue = 14f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glowDrift"
    )
    Box(
        modifier
            .graphicsLayer {
                translationX = drift
                translationY = -drift / 2f
            }
            .background(color.copy(alpha = 0.16f), CircleShape)
    )
}
