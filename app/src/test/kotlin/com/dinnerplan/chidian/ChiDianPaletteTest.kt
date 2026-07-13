package com.dinnerplan.chidian

import androidx.compose.ui.graphics.toArgb
import com.dinnerplan.chidian.ui.theme.ChiDianColors
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChiDianPaletteTest {
    @Test
    fun paletteUsesBrightAppetiteBase() {
        assertEquals(0xFFFFFBF4, ChiDianColors.Canvas.toArgb().toLong() and 0xFFFFFFFF)
        assertEquals(0xFFFF4F3E, ChiDianColors.Tomato.toArgb().toLong() and 0xFFFFFFFF)
        assertEquals(0xFF35D07F, ChiDianColors.Mint.toArgb().toLong() and 0xFFFFFFFF)
    }

    @Test
    fun paletteProvidesRoleColorsForConsistentFoodTheme() {
        assertEquals(0xFFC8372B, ChiDianColors.ActionPrimary.toArgb().toLong() and 0xFFFFFFFF)
        assertEquals(0xFFFFF4EA, ChiDianColors.ActionPrimarySoft.toArgb().toLong() and 0xFFFFFFFF)
        assertEquals(0xFF176C49, ChiDianColors.LocationAccent.toArgb().toLong() and 0xFFFFFFFF)
        assertEquals(0xFFF0FBF5, ChiDianColors.LocationAccentSoft.toArgb().toLong() and 0xFFFFFFFF)
        assertEquals(0xFFFFF8F0, ChiDianColors.SurfaceSubtle.toArgb().toLong() and 0xFFFFFFFF)
        assertEquals(0xFFF0DED2, ChiDianColors.BorderSubtle.toArgb().toLong() and 0xFFFFFFFF)
    }

    @Test
    fun primaryAndLocationActionColorsHaveReadableWhiteText() {
        assertTrue(contrastRatio(0xFFFFFFFF, 0xFFC8372B) > 4.5)
        assertTrue(contrastRatio(0xFFFFFFFF, 0xFF176C49) > 4.5)
    }

    @Test
    fun aiAccentIsOnlyAnAccentNotTheMainPalette() {
        val mainColors = listOf(ChiDianColors.ActionPrimary, ChiDianColors.ActionPrimarySoft, ChiDianColors.LocationAccent)
        assertTrue(ChiDianColors.AiBlue !in mainColors)
        assertTrue(ChiDianColors.AiCyan !in mainColors)
        assertTrue(ChiDianColors.Sun !in mainColors)
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
