package com.dinnerplan.chidian

import com.dinnerplan.chidian.ui.theme.AppThemeStyle
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThemeFeatureTest {
    @Test
    fun firstInstallUsesDefaultThemeAndThemeCopyPreservesBusinessState() {
        val original = AppUiState(
            cookQuery = "番茄牛肉",
            restaurantQuery = "甜品",
            locationText = "杭州西湖",
            savedRecipeIds = setOf("recipe")
        )

        val themed = original.copy(themeStyle = AppThemeStyle.GirlPink)

        assertEquals(AppThemeStyle.Default, original.themeStyle)
        assertEquals(AppThemeStyle.GirlPink, themed.themeStyle)
        assertEquals(original.cookQuery, themed.cookQuery)
        assertEquals(original.restaurantQuery, themed.restaurantQuery)
        assertEquals(original.locationText, themed.locationText)
        assertEquals(original.savedRecipeIds, themed.savedRecipeIds)
        assertEquals(original.preferences, themed.preferences)
        assertEquals(original.developerSettings, themed.developerSettings)
    }

    @Test
    fun themeSelectionUsesExistingDataStoreLifecycle() {
        val mainActivity = readProjectFile("app/src/main/java/com/dinnerplan/chidian/MainActivity.kt")
        val settingsScreen = readProjectFile(
            "app/src/main/java/com/dinnerplan/chidian/ui/screens/SavedSettingsScreens.kt"
        )

        assertTrue("stringPreferencesKey(\"theme_style\")" in mainActivity)
        assertTrue("AppThemeStyle.fromStorageId(preferences[SettingsKeys.ThemeStyle])" in mainActivity)
        assertTrue("preferences[SettingsKeys.ThemeStyle] = state.themeStyle.storageId" in mainActivity)
        assertTrue("uiState.themeStyle" in mainActivity)
        assertTrue("ChiDianTheme(style = uiState.themeStyle)" in mainActivity)
        assertTrue("ThemePickerCard(" in settingsScreen)
        assertTrue("state.copy(themeStyle = style)" in settingsScreen)
        assertTrue("rememberSaveable" in settingsScreen)
        assertTrue("expanded = false" in settingsScreen)
        assertTrue("AppIcon.ExpandMore" in settingsScreen)
        assertTrue("DropdownMenu(" in settingsScreen)
        assertTrue("DropdownMenuItem(" in settingsScreen)
        assertTrue("ThemeOptionRow" !in settingsScreen)
        assertTrue("ThemeOptionCard" !in settingsScreen)
        assertTrue("\"默认主题\"" in settingsScreen)
        assertTrue("\"少女粉\"" in settingsScreen)
    }

    @Test
    fun refinementKeepsDecorationsAtScreenFootersAndRemovesDecisionSubcopy() {
        val homeScreen = readProjectFile(
            "app/src/main/java/com/dinnerplan/chidian/ui/screens/HomeScreen.kt"
        )
        val savedSettingsScreen = readProjectFile(
            "app/src/main/java/com/dinnerplan/chidian/ui/screens/SavedSettingsScreens.kt"
        )

        assertTrue("decisionSubcopyForHour" !in homeScreen)
        assertTrue("SavedFooterDecoration()" in savedSettingsScreen)
        assertTrue("R.drawable.girl_pink_bunny_chef" in savedSettingsScreen)
        assertTrue(
            savedSettingsScreen.indexOf("ThemePickerCard(") <
                savedSettingsScreen.indexOf("R.drawable.girl_pink_bear_bowl")
        )
    }

    @Test
    fun generatedDecorationsAndSemanticIconLayerAreProjectResources() {
        val appRoot = projectPath("app")
        val iconLayer = appRoot.resolve(
            "src/main/java/com/dinnerplan/chidian/ui/components/ThemedIcons.kt"
        ).toFile()
        val bunny = appRoot.resolve(
            "src/main/res/drawable-nodpi/girl_pink_bunny_chef.png"
        ).toFile()
        val bear = appRoot.resolve(
            "src/main/res/drawable-nodpi/girl_pink_bear_bowl.png"
        ).toFile()
        val defaultDecisionBackground = appRoot.resolve(
            "src/main/res/drawable-nodpi/decision_card_default_v2.png"
        ).toFile()
        val pinkDecisionBackground = appRoot.resolve(
            "src/main/res/drawable-nodpi/decision_card_girl_pink_v2.png"
        ).toFile()

        assertTrue(iconLayer.exists())
        assertTrue("enum class AppIcon" in iconLayer.readText())
        assertTrue("fun ThemedActionIcon" in iconLayer.readText())
        assertTrue(bunny.exists() && bunny.length() > 0)
        assertTrue(bear.exists() && bear.length() > 0)
        assertTrue(defaultDecisionBackground.exists() && defaultDecisionBackground.length() > 0)
        assertTrue(pinkDecisionBackground.exists() && pinkDecisionBackground.length() > 0)
    }

    private fun readProjectFile(relativePath: String): String {
        return projectPath(relativePath).toFile().readText()
    }

    private fun projectPath(relativePath: String): Path {
        val candidates = listOf(Path.of(relativePath), Path.of("..").resolve(relativePath))
        return candidates.firstOrNull { it.toFile().exists() }
            ?: error("Cannot find project file: $relativePath")
    }
}
