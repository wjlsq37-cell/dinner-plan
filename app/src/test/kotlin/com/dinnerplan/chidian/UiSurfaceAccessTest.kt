package com.dinnerplan.chidian

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UiSurfaceAccessTest {
    @Test
    fun uiModelsCanBeUsedOutsideMainActivityFile() {
        val recipe = Recipe(
            id = "recipe_test",
            name = "番茄炒蛋",
            cuisine = "家常菜",
            taste = listOf("酸甜"),
            tags = listOf("快手"),
            difficulty = "简单",
            cookTime = "15 分钟",
            servings = "2 人份",
            coverUrl = "",
            reason = "测试用菜谱",
            ingredients = listOf("鸡蛋" to "2个"),
            steps = listOf("炒鸡蛋", "炒番茄"),
            tips = "先炒鸡蛋。",
            ratingStars = 4.6,
            source = "测试"
        )
        val state = AppUiState(recipes = listOf(recipe), recommendMode = RecommendMode.SingleRecipe)

        assertEquals("番茄炒蛋", state.recipes.single().name)
        assertEquals("4.6 星 · 家常菜 · 15 分钟 · 简单", recipeMetaLine(recipe))
    }

    @Test
    fun restaurantSortingHelperRemainsStable() {
        val near = MockData.restaurants.first().copy(id = "near", distance = "300m", rating = "4.1")
        val far = MockData.restaurants.first().copy(id = "far", distance = "1.2km", rating = "4.9")

        assertEquals(listOf("near", "far"), sortRestaurants(listOf(far, near), RestaurantSortMode.Distance).map { it.id })
        assertEquals(listOf("far", "near"), sortRestaurants(listOf(near, far), RestaurantSortMode.Rating).map { it.id })
        assertTrue(sortModeLabel(RestaurantSortMode.Relevance).contains("相关度"))
    }

    @Test
    fun detailScreensLiveInScreensPackage() {
        val methods = Class.forName("com.dinnerplan.chidian.ui.screens.DetailScreensKt")
            .declaredMethods
            .map { it.name }
            .toSet()

        assertTrue("MealPlanDetailScreen" in methods)
        assertTrue("RecipeDetailScreen" in methods)
        assertTrue("RestaurantDetailScreen" in methods)
    }

    @Test
    fun savedAndSettingsScreensLiveInScreensPackage() {
        val methods = Class.forName("com.dinnerplan.chidian.ui.screens.SavedSettingsScreensKt")
            .declaredMethods
            .map { it.name }
            .toSet()

        assertTrue("SavedScreen" in methods)
        assertTrue("SettingsScreen" in methods)
        assertTrue("DeveloperSettingsScreen" in methods)
    }

    @Test
    fun cookAndNearbyScreensUseStrongHeadersWithoutRedundantIntroCards() {
        val cookScreen = readProjectFile("app/src/main/java/com/dinnerplan/chidian/ui/screens/CookRecommendScreen.kt")
        val nearbyScreen = readProjectFile("app/src/main/java/com/dinnerplan/chidian/ui/screens/NearbyRestaurantScreen.kt")
        val scaffoldComponents = readProjectFile("app/src/main/java/com/dinnerplan/chidian/ui/components/FoodScaffold.kt")

        assertTrue("fun FoodScreenHeader(" in scaffoldComponents)
        assertFalse("这一餐的方向" in cookScreen)
        assertFalse("CookInsightCard(" in cookScreen)
        assertFalse("附近雷达" in nearbyScreen)
        assertFalse("NearbyRadarHeader(" in nearbyScreen)
    }

    @Test
    fun highlightedControlsAndWarningTriangleAreRemoved() {
        val homeScreen = readProjectFile("app/src/main/java/com/dinnerplan/chidian/ui/screens/HomeScreen.kt")
        val nearbyScreen = readProjectFile("app/src/main/java/com/dinnerplan/chidian/ui/screens/NearbyRestaurantScreen.kt")
        val cookScreen = readProjectFile("app/src/main/java/com/dinnerplan/chidian/ui/screens/CookRecommendScreen.kt")

        assertFalse("菜谱库" in homeScreen)
        assertFalse("AI 生成" in homeScreen)
        assertFalse("高德附近" in homeScreen)
        assertFalse("NearbyFilterTags(" in nearbyScreen)
        assertFalse("val tags = listOf(\"5km\", \"营业中\", \"川湘菜\", \"人均 50 内\")" in nearbyScreen)
        assertFalse("Icons.Filled.Warning" in cookScreen)
        assertFalse("Icons.Filled.Warning" in nearbyScreen)
    }

    @Test
    fun compactNavigationControlsUseCenteredStableLayout() {
        val homeScreen = readProjectFile("app/src/main/java/com/dinnerplan/chidian/ui/screens/HomeScreen.kt")
        val savedScreen = readProjectFile("app/src/main/java/com/dinnerplan/chidian/ui/screens/SavedSettingsScreens.kt")
        val foodControls = readProjectFile("app/src/main/java/com/dinnerplan/chidian/ui/components/FoodControls.kt")

        assertTrue("TextAlign.Center" in homeScreen)
        assertTrue("TextAlign.Center" in savedScreen)
        assertTrue(".height(46.dp)" in foodControls)
        assertTrue(".fillMaxHeight()" in foodControls)
        assertTrue("contentAlignment = Alignment.Center" in foodControls)
        assertTrue("shadowElevation = 0.dp" in foodControls)
    }

    @Test
    fun cookHeaderSnackbarsAndSegmentRippleStayClean() {
        val mainActivity = readProjectFile("app/src/main/java/com/dinnerplan/chidian/MainActivity.kt")
        val cookScreen = readProjectFile("app/src/main/java/com/dinnerplan/chidian/ui/screens/CookRecommendScreen.kt")
        val foodControls = readProjectFile("app/src/main/java/com/dinnerplan/chidian/ui/components/FoodControls.kt")

        assertFalse("state.cookQuery" in cookScreen.substringAfter("FoodScreenHeader(").substringBefore("onBack = onBack"))
        assertFalse("showSnackbar" in mainActivity)
        assertFalse("SnackbarHost(" in mainActivity)
        assertTrue("indication = null" in foodControls)
        assertFalse("modifier = modifier\n            .fillMaxHeight()\n            .clickable(onClick = onClick)" in foodControls)
    }

    private fun readProjectFile(relativePath: String): String {
        val candidates = listOf(Path.of(relativePath), Path.of("..").resolve(relativePath))
        val path = candidates.firstOrNull { it.toFile().exists() }
            ?: error("Cannot find project file: $relativePath")
        return path.toFile().readText()
    }
}
