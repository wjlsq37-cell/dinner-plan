package com.dinnerplan.chidian

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MapNavigationTest {
    @Test
    fun coordinateTargetUsesAmapAppRouteWithoutBrowserFallback() {
        val targets = buildNavigationTargets(
            RestaurantNavigationRequest(
                name = "隐泉寿司",
                address = "杭州市海粟中心",
                latitude = 30.256,
                longitude = 120.205
            )
        )

        val amapUri = requireNotNull(targets.amapUri)
        assertTrue(amapUri.startsWith("androidamap://"))
        assertTrue(amapUri.contains("dlat=30.256") || amapUri.contains("lat=30.256"))
        assertTrue(amapUri.contains("dlon=120.205") || amapUri.contains("lon=120.205"))
        assertFalse(amapUri.contains("https://uri.amap.com"))
    }

    @Test
    fun addressOnlyTargetStillUsesAmapAppSearch() {
        val targets = buildNavigationTargets(
            RestaurantNavigationRequest(
                name = "隐泉寿司",
                address = "杭州市海粟中心",
                latitude = null,
                longitude = null
            )
        )

        val amapUri = requireNotNull(targets.amapUri)
        assertTrue(amapUri.startsWith("androidamap://"))
        assertTrue(amapUri.contains("keywords=") || amapUri.contains("dname="))
        assertFalse(amapUri.contains("https://uri.amap.com"))
    }

    @Test
    fun mapNavigationDoesNotAttemptWebOrGenericGeoFallbacks() {
        val source = java.nio.file.Path.of("app/src/main/java/com/dinnerplan/chidian/MapNavigation.kt")
            .takeIf { it.toFile().exists() }
            ?: java.nio.file.Path.of("src/main/java/com/dinnerplan/chidian/MapNavigation.kt")
        val text = source.toFile().readText()

        assertTrue("setPackage(\"com.autonavi.minimap\")" in text)
        assertFalse("browserUri" in text)
        assertFalse("https://uri.amap.com" in text)
        assertFalse("geo:" in text)
    }

    @Test
    fun manifestDeclaresAmapPackageQuery() {
        val source = java.nio.file.Path.of("app/src/main/AndroidManifest.xml")
            .takeIf { it.toFile().exists() }
            ?: java.nio.file.Path.of("src/main/AndroidManifest.xml")
        val text = source.toFile().readText()

        assertTrue("<queries>" in text)
        assertTrue("com.autonavi.minimap" in text)
    }
}
