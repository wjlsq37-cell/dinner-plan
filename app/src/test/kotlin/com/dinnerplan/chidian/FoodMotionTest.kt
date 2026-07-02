package com.dinnerplan.chidian

import com.dinnerplan.chidian.ui.components.staggerDelayMillis
import kotlin.test.Test
import kotlin.test.assertEquals

class FoodMotionTest {
    @Test
    fun staggerDelayIsBoundedForLongLists() {
        assertEquals(0, staggerDelayMillis(0))
        assertEquals(55, staggerDelayMillis(1))
        assertEquals(220, staggerDelayMillis(4))
        assertEquals(220, staggerDelayMillis(20))
    }
}
