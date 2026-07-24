package com.dinnerplan.chidian

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LaunchAndIconSettingsTest {
    @Test
    fun launcherIconStorageFallsBackToClassic() {
        assertEquals(LauncherIconStyle.Classic, LauncherIconStyle.fromStorageId(null))
        assertEquals(LauncherIconStyle.Classic, LauncherIconStyle.fromStorageId("unknown"))
        assertEquals(LauncherIconStyle.Classic, LauncherIconStyle.fromStorageId("classic"))
        assertEquals(LauncherIconStyle.EnergyChef, LauncherIconStyle.fromStorageId("energy_chef"))
    }

    @Test
    fun androidDisablesQuickRecipePriority() {
        assertFalse(ANDROID_PREFER_QUICK_RECIPES)
    }

    @Test
    fun launchArtworkStaysVisibleForAtLeastOnePointEightSeconds() {
        assertTrue(LAUNCH_SPLASH_MIN_VISIBLE_MILLIS >= 1_800L)
    }
}
