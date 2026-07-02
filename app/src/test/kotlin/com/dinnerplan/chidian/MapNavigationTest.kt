package com.dinnerplan.chidian

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapNavigationTest {
    @Test
    fun coordinateTargetPrefersAmapRouteAndGeoFallback() {
        val targets = buildNavigationTargets(
            RestaurantNavigationRequest(
                name = "隐泉寿司",
                address = "杭州市海粟中心",
                latitude = 30.256,
                longitude = 120.205
            )
        )

        assertEquals(
            "androidamap://route/plan/?sourceApplication=吃点啥&dlat=30.256&dlon=120.205&dname=%E9%9A%90%E6%B3%89%E5%AF%BF%E5%8F%B8&dev=0&t=0",
            targets.amapUri
        )
        assertEquals("geo:30.256,120.205?q=30.256,120.205(%E9%9A%90%E6%B3%89%E5%AF%BF%E5%8F%B8)", targets.geoUri)
        assertTrue(targets.browserUri.contains("120.205,30.256"))
    }

    @Test
    fun addressOnlyTargetUsesGeoSearch() {
        val targets = buildNavigationTargets(
            RestaurantNavigationRequest(
                name = "隐泉寿司",
                address = "杭州市海粟中心",
                latitude = null,
                longitude = null
            )
        )

        assertEquals(null, targets.amapUri)
        assertEquals("geo:0,0?q=%E6%9D%AD%E5%B7%9E%E5%B8%82%E6%B5%B7%E7%B2%9F%E4%B8%AD%E5%BF%83%20%E9%9A%90%E6%B3%89%E5%AF%BF%E5%8F%B8", targets.geoUri)
        assertTrue(targets.browserUri.contains("%E6%9D%AD%E5%B7%9E%E5%B8%82%E6%B5%B7%E7%B2%9F%E4%B8%AD%E5%BF%83"))
    }
}
