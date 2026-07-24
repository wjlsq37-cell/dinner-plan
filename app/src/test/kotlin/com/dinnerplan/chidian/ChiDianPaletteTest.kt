package com.dinnerplan.chidian

import androidx.compose.ui.graphics.toArgb
import com.dinnerplan.chidian.ui.theme.AppThemeStyle
import com.dinnerplan.chidian.ui.theme.ChiDianPalettes
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChiDianPaletteTest {
    @Test
    fun paletteUsesBrightAppetiteBase() {
        assertEquals(0xFFFFFBF4, ChiDianPalettes.Default.canvas.toArgb().toLong() and 0xFFFFFFFF)
        assertEquals(0xFFFF4F3E, ChiDianPalettes.Default.tomato.toArgb().toLong() and 0xFFFFFFFF)
        assertEquals(0xFF35D07F, ChiDianPalettes.Default.mint.toArgb().toLong() and 0xFFFFFFFF)
    }

    @Test
    fun paletteProvidesRoleColorsForConsistentFoodTheme() {
        val palette = ChiDianPalettes.Default
        assertEquals(0xFFC8372B, palette.actionPrimary.toArgb().toLong() and 0xFFFFFFFF)
        assertEquals(0xFFFFF4EA, palette.actionPrimarySoft.toArgb().toLong() and 0xFFFFFFFF)
        assertEquals(0xFF176C49, palette.locationAccent.toArgb().toLong() and 0xFFFFFFFF)
        assertEquals(0xFFF0FBF5, palette.locationAccentSoft.toArgb().toLong() and 0xFFFFFFFF)
        assertEquals(0xFFFFF8F0, palette.surfaceSubtle.toArgb().toLong() and 0xFFFFFFFF)
        assertEquals(0xFFF0DED2, palette.borderSubtle.toArgb().toLong() and 0xFFFFFFFF)
    }

    @Test
    fun primaryAndLocationActionColorsHaveReadableWhiteText() {
        assertTrue(contrastRatio(0xFFFFFFFF, 0xFFC8372B) > 4.5)
        assertTrue(contrastRatio(0xFFFFFFFF, 0xFF176C49) > 4.5)
    }

    @Test
    fun aiAccentIsOnlyAnAccentNotTheMainPalette() {
        val palette = ChiDianPalettes.Default
        val mainColors = listOf(palette.actionPrimary, palette.actionPrimarySoft, palette.locationAccent)
        assertTrue(palette.aiBlue !in mainColors)
        assertTrue(palette.aiCyan !in mainColors)
        assertTrue(palette.sun !in mainColors)
    }

    @Test
    fun girlPinkPaletteMatchesApprovedThemeTokens() {
        val palette = ChiDianPalettes.GirlPink
        assertEquals(0xFFFFF7F3, palette.canvas.toArgb().toLong() and 0xFFFFFFFF)
        assertEquals(0xFFFFFFFC, palette.surface.toArgb().toLong() and 0xFFFFFFFF)
        assertEquals(0xFFFFF0ED, palette.surfaceWarm.toArgb().toLong() and 0xFFFFFFFF)
        assertEquals(0xFFC93F62, palette.actionPrimary.toArgb().toLong() and 0xFFFFFFFF)
        assertEquals(0xFFFFE5EB, palette.actionPrimarySoft.toArgb().toLong() and 0xFFFFFFFF)
        assertEquals(0xFFB94666, palette.locationAccent.toArgb().toLong() and 0xFFFFFFFF)
        assertEquals(0xFFF5C9D2, palette.borderSubtle.toArgb().toLong() and 0xFFFFFFFF)
        assertEquals(0xFF3B2527, palette.ink.toArgb().toLong() and 0xFFFFFFFF)
        assertEquals(0xFF795D60, palette.muted.toArgb().toLong() and 0xFFFFFFFF)
    }

    @Test
    fun girlPinkPrimaryActionsKeepReadableWhiteText() {
        assertTrue(contrastRatio(0xFFFFFFFF, 0xFFC93F62) > 4.5)
        assertTrue(contrastRatio(0xFFFFFFFF, 0xFFB94666) > 4.5)
    }

    @Test
    fun themeStorageIdsDecodeAndUnknownValuesFallBackToDefault() {
        assertEquals(AppThemeStyle.Default, AppThemeStyle.fromStorageId(null))
        assertEquals(AppThemeStyle.Default, AppThemeStyle.fromStorageId("legacy"))
        assertEquals(AppThemeStyle.Default, AppThemeStyle.fromStorageId("default"))
        assertEquals(AppThemeStyle.GirlPink, AppThemeStyle.fromStorageId("girl_pink"))
    }

    private fun contrastRatio(foregroundArgb: Long, backgroundArgb: Long): Double {
        val foreground = relativeLuminance(foregroundArgb)
        val background = relativeLuminance(backgroundArgb)
        val lighter = maxOf(foreground, background)
        val darker = minOf(foreground, background)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(argb: Long): Double {
        val r = ((argb shr 16) and 0xFF) / 255.0
        val g = ((argb shr 8) and 0xFF) / 255.0
        val b = (argb and 0xFF) / 255.0
        fun channel(value: Double): Double {
            return if (value <= 0.03928) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
    }
}
