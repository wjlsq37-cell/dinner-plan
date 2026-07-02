package com.dinnerplan.chidian

import androidx.compose.ui.graphics.toArgb
import com.dinnerplan.chidian.ui.theme.ChiDianColors
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
    fun aiAccentIsOnlyAnAccentNotTheMainPalette() {
        val mainColors = listOf(ChiDianColors.Tomato, ChiDianColors.Sun, ChiDianColors.Mint)
        assertTrue(ChiDianColors.AiBlue !in mainColors)
    }
}
