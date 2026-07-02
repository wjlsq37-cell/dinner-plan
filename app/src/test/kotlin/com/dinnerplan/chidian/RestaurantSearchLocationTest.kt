package com.dinnerplan.chidian

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RestaurantSearchLocationTest {
    @Test
    fun currentLocationUsesCoordinates() {
        val location = restaurantLocationForSearch("当前位置", 30.256, 120.205)

        assertEquals(30.256, location.latitude)
        assertEquals(120.205, location.longitude)
        assertEquals("当前位置", location.text)
    }

    @Test
    fun manualLocationUsesTextOnly() {
        val location = restaurantLocationForSearch("杭州市海粟中心", null, null)

        assertNull(location.latitude)
        assertNull(location.longitude)
        assertEquals("杭州市海粟中心", location.text)
    }

    @Test
    fun coordinateFallbackLabelIsReadable() {
        assertEquals("经纬度：30.256000, 120.205000", coordinateDisplayLabel(30.256, 120.205))
    }

    @Test
    fun amapSearchCoordinatesMustBeInsideMainlandBounds() {
        assertEquals(true, isLikelyAmapSearchCoordinate(30.256, 120.205))
        assertEquals(false, isLikelyAmapSearchCoordinate(37.422, -122.084))
    }
}
