package com.dinnerplan.chidian.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

enum class AppThemeStyle(val storageId: String) {
    Default("default"),
    GirlPink("girl_pink");

    companion object {
        fun fromStorageId(value: String?): AppThemeStyle {
            return entries.firstOrNull { it.storageId == value } ?: Default
        }
    }
}

data class ChiDianPalette(
    val canvas: Color,
    val surface: Color,
    val surfaceWarm: Color,
    val line: Color,
    val tomato: Color,
    val tomatoDark: Color,
    val sun: Color,
    val orange: Color,
    val mint: Color,
    val mintDark: Color,
    val aiBlue: Color,
    val aiCyan: Color,
    val ink: Color,
    val muted: Color,
    val softMuted: Color,
    val actionPrimary: Color,
    val actionPrimarySoft: Color,
    val locationAccent: Color,
    val locationAccentSoft: Color,
    val surfaceSubtle: Color,
    val borderSubtle: Color
)

object ChiDianPalettes {
    val Default = ChiDianPalette(
        canvas = Color(0xFFFFFBF4),
        surface = Color(0xFFFFFFFF),
        surfaceWarm = Color(0xFFFFF6E8),
        line = Color(0xFFFFDCC7),
        tomato = Color(0xFFFF4F3E),
        tomatoDark = Color(0xFFC8372B),
        sun = Color(0xFFFFC857),
        orange = Color(0xFFFF8A3D),
        mint = Color(0xFF35D07F),
        mintDark = Color(0xFF1F8F5A),
        aiBlue = Color(0xFF426BFF),
        aiCyan = Color(0xFF18D9C5),
        ink = Color(0xFF201A16),
        muted = Color(0xFF746A61),
        softMuted = Color(0xFF9E9188),
        actionPrimary = Color(0xFFC8372B),
        actionPrimarySoft = Color(0xFFFFF4EA),
        locationAccent = Color(0xFF176C49),
        locationAccentSoft = Color(0xFFF0FBF5),
        surfaceSubtle = Color(0xFFFFF8F0),
        borderSubtle = Color(0xFFF0DED2)
    )

    val GirlPink = ChiDianPalette(
        canvas = Color(0xFFFFF7F3),
        surface = Color(0xFFFFFFFC),
        surfaceWarm = Color(0xFFFFF0ED),
        line = Color(0xFFF3BAC6),
        tomato = Color(0xFFE8677F),
        tomatoDark = Color(0xFFC93F62),
        sun = Color(0xFFF2A33A),
        orange = Color(0xFFE87A5C),
        mint = Color(0xFF75A76D),
        mintDark = Color(0xFF507C4B),
        aiBlue = Color(0xFF6F5DB7),
        aiCyan = Color(0xFFA74F7B),
        ink = Color(0xFF3B2527),
        muted = Color(0xFF795D60),
        softMuted = Color(0xFF9D7E82),
        actionPrimary = Color(0xFFC93F62),
        actionPrimarySoft = Color(0xFFFFE5EB),
        locationAccent = Color(0xFFB94666),
        locationAccentSoft = Color(0xFFFFEAF0),
        surfaceSubtle = Color(0xFFFFF4F2),
        borderSubtle = Color(0xFFF5C9D2)
    )
}

data class ChiDianStyleTokens(
    val themeStyle: AppThemeStyle,
    val cardShape: Shape,
    val heroShape: Shape,
    val controlShape: Shape,
    val buttonShape: Shape,
    val iconShape: Shape
) {
    val isGirlPink: Boolean get() = themeStyle == AppThemeStyle.GirlPink
}

private val DefaultStyleTokens = ChiDianStyleTokens(
    themeStyle = AppThemeStyle.Default,
    cardShape = RoundedCornerShape(8.dp),
    heroShape = RoundedCornerShape(8.dp),
    controlShape = RoundedCornerShape(8.dp),
    buttonShape = RoundedCornerShape(8.dp),
    iconShape = RoundedCornerShape(999.dp)
)

private val GirlPinkStyleTokens = ChiDianStyleTokens(
    themeStyle = AppThemeStyle.GirlPink,
    cardShape = RoundedCornerShape(20.dp),
    heroShape = RoundedCornerShape(22.dp),
    controlShape = RoundedCornerShape(16.dp),
    buttonShape = RoundedCornerShape(999.dp),
    iconShape = RoundedCornerShape(14.dp)
)

private val LocalChiDianPalette = staticCompositionLocalOf { ChiDianPalettes.Default }
private val LocalChiDianStyleTokens = staticCompositionLocalOf { DefaultStyleTokens }

object ChiDianColors {
    val Canvas: Color @Composable @ReadOnlyComposable get() = LocalChiDianPalette.current.canvas
    val Surface: Color @Composable @ReadOnlyComposable get() = LocalChiDianPalette.current.surface
    val SurfaceWarm: Color @Composable @ReadOnlyComposable get() = LocalChiDianPalette.current.surfaceWarm
    val Line: Color @Composable @ReadOnlyComposable get() = LocalChiDianPalette.current.line
    val Tomato: Color @Composable @ReadOnlyComposable get() = LocalChiDianPalette.current.tomato
    val TomatoDark: Color @Composable @ReadOnlyComposable get() = LocalChiDianPalette.current.tomatoDark
    val Sun: Color @Composable @ReadOnlyComposable get() = LocalChiDianPalette.current.sun
    val Orange: Color @Composable @ReadOnlyComposable get() = LocalChiDianPalette.current.orange
    val Mint: Color @Composable @ReadOnlyComposable get() = LocalChiDianPalette.current.mint
    val MintDark: Color @Composable @ReadOnlyComposable get() = LocalChiDianPalette.current.mintDark
    val AiBlue: Color @Composable @ReadOnlyComposable get() = LocalChiDianPalette.current.aiBlue
    val AiCyan: Color @Composable @ReadOnlyComposable get() = LocalChiDianPalette.current.aiCyan
    val Ink: Color @Composable @ReadOnlyComposable get() = LocalChiDianPalette.current.ink
    val Muted: Color @Composable @ReadOnlyComposable get() = LocalChiDianPalette.current.muted
    val SoftMuted: Color @Composable @ReadOnlyComposable get() = LocalChiDianPalette.current.softMuted
    val ActionPrimary: Color @Composable @ReadOnlyComposable get() = LocalChiDianPalette.current.actionPrimary
    val ActionPrimarySoft: Color @Composable @ReadOnlyComposable get() = LocalChiDianPalette.current.actionPrimarySoft
    val LocationAccent: Color @Composable @ReadOnlyComposable get() = LocalChiDianPalette.current.locationAccent
    val LocationAccentSoft: Color @Composable @ReadOnlyComposable get() = LocalChiDianPalette.current.locationAccentSoft
    val SurfaceSubtle: Color @Composable @ReadOnlyComposable get() = LocalChiDianPalette.current.surfaceSubtle
    val BorderSubtle: Color @Composable @ReadOnlyComposable get() = LocalChiDianPalette.current.borderSubtle
}

object ChiDianGradients {
    val AppetiteHero: Brush
        @Composable @ReadOnlyComposable
        get() {
            val palette = LocalChiDianPalette.current
            return if (ChiDianThemeValues.isGirlPink) {
                Brush.linearGradient(
                    listOf(Color(0xFFB93657), palette.actionPrimary, palette.locationAccent)
                )
            } else {
                Brush.linearGradient(
                    listOf(palette.actionPrimary, palette.orange, palette.actionPrimary)
                )
            }
        }

    val NearbyHero: Brush
        @Composable @ReadOnlyComposable
        get() {
            val palette = LocalChiDianPalette.current
            return if (ChiDianThemeValues.isGirlPink) {
                Brush.linearGradient(
                    listOf(palette.locationAccent, palette.actionPrimary, Color(0xFF9E3554))
                )
            } else {
                Brush.linearGradient(
                    listOf(palette.locationAccent, palette.locationAccentSoft, palette.surfaceSubtle)
                )
            }
        }

    val AiGlow: Brush
        @Composable @ReadOnlyComposable
        get() {
            val palette = LocalChiDianPalette.current
            return Brush.linearGradient(
                listOf(palette.aiBlue, palette.aiCyan, palette.actionPrimary)
            )
        }
}

object ChiDianThemeValues {
    val style: AppThemeStyle
        @Composable @ReadOnlyComposable get() = LocalChiDianStyleTokens.current.themeStyle
    val isGirlPink: Boolean
        @Composable @ReadOnlyComposable get() = LocalChiDianStyleTokens.current.isGirlPink
    val cardShape: Shape
        @Composable @ReadOnlyComposable get() = LocalChiDianStyleTokens.current.cardShape
    val heroShape: Shape
        @Composable @ReadOnlyComposable get() = LocalChiDianStyleTokens.current.heroShape
    val controlShape: Shape
        @Composable @ReadOnlyComposable get() = LocalChiDianStyleTokens.current.controlShape
    val buttonShape: Shape
        @Composable @ReadOnlyComposable get() = LocalChiDianStyleTokens.current.buttonShape
    val iconShape: Shape
        @Composable @ReadOnlyComposable get() = LocalChiDianStyleTokens.current.iconShape
}

private val DefaultShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

private val GirlPinkShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

private fun colorScheme(palette: ChiDianPalette) = lightColorScheme(
    primary = palette.actionPrimary,
    onPrimary = Color.White,
    primaryContainer = palette.actionPrimarySoft,
    onPrimaryContainer = palette.actionPrimary,
    secondary = palette.locationAccent,
    onSecondary = Color.White,
    secondaryContainer = palette.locationAccentSoft,
    onSecondaryContainer = palette.locationAccent,
    background = palette.canvas,
    surface = palette.surface,
    surfaceVariant = palette.surfaceSubtle,
    onSurface = palette.ink,
    onSurfaceVariant = palette.muted,
    outline = palette.borderSubtle
)

@Composable
fun ChiDianTheme(
    style: AppThemeStyle = AppThemeStyle.Default,
    content: @Composable () -> Unit
) {
    val palette = when (style) {
        AppThemeStyle.Default -> ChiDianPalettes.Default
        AppThemeStyle.GirlPink -> ChiDianPalettes.GirlPink
    }
    val tokens = when (style) {
        AppThemeStyle.Default -> DefaultStyleTokens
        AppThemeStyle.GirlPink -> GirlPinkStyleTokens
    }
    val shapes = when (style) {
        AppThemeStyle.Default -> DefaultShapes
        AppThemeStyle.GirlPink -> GirlPinkShapes
    }

    CompositionLocalProvider(
        LocalChiDianPalette provides palette,
        LocalChiDianStyleTokens provides tokens
    ) {
        MaterialTheme(
            colorScheme = colorScheme(palette),
            shapes = shapes,
            content = content
        )
    }
}
