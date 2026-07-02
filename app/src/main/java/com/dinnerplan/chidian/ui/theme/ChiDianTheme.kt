package com.dinnerplan.chidian.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object ChiDianColors {
    val Canvas = Color(0xFFFFFBF4)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceWarm = Color(0xFFFFF6E8)
    val Line = Color(0xFFFFDCC7)
    val Tomato = Color(0xFFFF4F3E)
    val TomatoDark = Color(0xFFC8372B)
    val Sun = Color(0xFFFFC857)
    val Orange = Color(0xFFFF8A3D)
    val Mint = Color(0xFF35D07F)
    val MintDark = Color(0xFF1F8F5A)
    val AiBlue = Color(0xFF426BFF)
    val AiCyan = Color(0xFF18D9C5)
    val Ink = Color(0xFF201A16)
    val Muted = Color(0xFF746A61)
    val SoftMuted = Color(0xFF9E9188)
}

object ChiDianGradients {
    val AppetiteHero: Brush
        get() = Brush.linearGradient(
            listOf(ChiDianColors.Tomato, ChiDianColors.Orange, ChiDianColors.Sun)
        )

    val NearbyHero: Brush
        get() = Brush.linearGradient(
            listOf(ChiDianColors.Mint, ChiDianColors.AiCyan, ChiDianColors.Sun)
        )

    val AiGlow: Brush
        get() = Brush.linearGradient(
            listOf(ChiDianColors.AiBlue, ChiDianColors.AiCyan, ChiDianColors.Tomato)
        )
}

val ChiDianShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

private val ChiDianLightScheme = lightColorScheme(
    primary = ChiDianColors.Tomato,
    onPrimary = Color.White,
    secondary = ChiDianColors.Mint,
    onSecondary = Color.White,
    background = ChiDianColors.Canvas,
    surface = ChiDianColors.Surface,
    onSurface = ChiDianColors.Ink,
    outline = ChiDianColors.Line
)

@Composable
fun ChiDianTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ChiDianLightScheme,
        shapes = ChiDianShapes,
        content = content
    )
}
