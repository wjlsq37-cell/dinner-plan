package com.dinnerplan.chidian

import com.dinnerplan.chidian.ui.components.FoodBottomTab
import com.dinnerplan.chidian.ui.components.foodBottomTabFor
import kotlin.test.Test
import kotlin.test.assertEquals

class FoodBottomNavigationTest {
    @Test
    fun mapsNestedScreensToTheirTopLevelBottomTab() {
        val cases = mapOf(
            Screen.Home to FoodBottomTab.Home,
            Screen.CookRecommend to FoodBottomTab.Home,
            Screen.MealPlanDetail("meal") to FoodBottomTab.Home,
            Screen.RecipeDetail("recipe") to FoodBottomTab.Home,
            Screen.NearbyRestaurant to FoodBottomTab.Nearby,
            Screen.RestaurantDetail("restaurant") to FoodBottomTab.Nearby,
            Screen.Saved to FoodBottomTab.Saved,
            Screen.Settings to FoodBottomTab.Settings,
            Screen.TastePreferences to FoodBottomTab.Settings,
            Screen.SearchSettings to FoodBottomTab.Settings,
            Screen.LauncherIconSettings to FoodBottomTab.Settings,
            Screen.DeveloperSettings to FoodBottomTab.Settings
        )

        cases.forEach { (screen, expectedTab) ->
            assertEquals(expectedTab, foodBottomTabFor(screen), "screen=$screen")
        }
    }
}
