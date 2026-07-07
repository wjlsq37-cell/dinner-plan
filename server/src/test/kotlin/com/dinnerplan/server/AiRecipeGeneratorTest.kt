package com.dinnerplan.server

import com.dinnerplan.shared.DishItemDto
import com.dinnerplan.shared.IngredientDto
import com.dinnerplan.shared.MealPlanDto
import com.dinnerplan.shared.RecipeDto
import com.dinnerplan.shared.RecommendationModeDto
import com.dinnerplan.shared.UserPreferenceDto
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiRecipeGeneratorTest {
    @Test
    fun promptIncludesAvoidsAndTastePreferences() {
        val prompt = AiRecipeGenerator.buildPrompt(
            query = "做一顿晚餐",
            mode = RecommendationModeDto.RECIPE_COMBO,
            preferences = UserPreferenceDto(tastes = listOf("清淡"), avoids = listOf("海鲜", "香菜"))
        )

        assertTrue(prompt.system.contains("必须避开忌口"))
        assertTrue(prompt.system.contains("尽量符合偏好"))
        assertTrue(prompt.user.contains("清淡"))
        assertTrue(prompt.user.contains("海鲜"))
        assertTrue(prompt.user.contains("香菜"))
    }

    @Test
    fun filtersAiGenerationWhenRecipeOrMealPlanContainsAvoids() {
        val generation = AiCookGeneration(
            intent = RecommendationModeDto.RECIPE_COMBO,
            summary = "晚餐",
            mealPlans = listOf(
                mealPlan("bad_meal", "鲜虾晚餐", "虾仁很鲜"),
                mealPlan("safe_meal", "豆腐晚餐", "清淡")
            ),
            recipes = listOf(
                recipe("bad_recipe", "香菜拌牛肉", listOf("牛肉", "香菜")),
                recipe("safe_recipe", "清炒豆腐", listOf("豆腐"))
            )
        )

        val filtered = AiRecipeGenerator.filterAvoidingTerms(generation, listOf("香菜", "牛羊肉", "海鲜"))

        assertFalse(filtered.recipes.any { it.id == "bad_recipe" })
        assertFalse(filtered.mealPlans.any { it.id == "bad_meal" })
        assertTrue(filtered.recipes.any { it.id == "safe_recipe" })
        assertTrue(filtered.mealPlans.any { it.id == "safe_meal" })
    }

    private fun recipe(id: String, name: String, ingredients: List<String>): RecipeDto {
        return RecipeDto(
            id = id,
            name = name,
            cuisine = "家常",
            taste = listOf("清淡"),
            tags = listOf("晚餐"),
            difficulty = "简单",
            cookTime = "20 分钟",
            servings = "2 人份",
            coverUrl = "",
            reason = "$name 做法",
            ingredients = ingredients.map { IngredientDto(it, "") },
            steps = listOf("处理${ingredients.joinToString()}"),
            tips = "按口味调整"
        )
    }

    private fun mealPlan(id: String, title: String, dishName: String): MealPlanDto {
        return MealPlanDto(
            id = id,
            title = title,
            structure = "一菜",
            cookTime = "20 分钟",
            servings = "2 人份",
            coverUrl = "",
            tags = listOf("晚餐"),
            reason = title,
            dishes = listOf(DishItemDto("菜品", dishName, "适合晚餐", "Veg")),
            shoppingList = listOf(dishName),
            timeline = listOf("先备菜")
        )
    }
}
