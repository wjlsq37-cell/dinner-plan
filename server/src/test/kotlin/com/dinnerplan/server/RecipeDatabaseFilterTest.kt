package com.dinnerplan.server

import com.dinnerplan.shared.IngredientDto
import com.dinnerplan.shared.RecipeDatabaseFilter
import com.dinnerplan.shared.RecipeDto
import com.dinnerplan.shared.UserPreferenceDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecipeDatabaseFilterTest {
    @Test
    fun hardFiltersAvoidsAndWeightsTastePreferences() {
        val recipes = listOf(
            recipe("beef", "番茄牛肉晚餐", ingredients = listOf("牛肉"), tags = listOf("晚餐", "酸甜")),
            recipe("light", "清淡豆腐晚餐", ingredients = listOf("豆腐"), tags = listOf("晚餐", "清淡")),
            recipe("spicy", "微辣鸡丁晚餐", ingredients = listOf("鸡肉"), tags = listOf("晚餐", "微辣"))
        )

        val result = RecipeDatabaseFilter.filterAndSort(
            recipes = recipes,
            query = "晚餐",
            preferences = UserPreferenceDto(tastes = listOf("清淡"), avoids = listOf("牛羊肉"))
        )

        assertEquals(listOf("light", "spicy"), result.map { it.id })
        assertFalse(result.any { it.id == "beef" })
    }

    @Test
    fun avoidTermsNeverActAsPositiveKeywords() {
        val avoided = recipe("shrimp", "鲜虾晚餐", ingredients = listOf("虾"), tags = listOf("海鲜", "晚餐"))
        val safe = recipe("veg", "青菜晚餐", ingredients = listOf("青菜"), tags = listOf("晚餐"))

        val result = RecipeDatabaseFilter.filterAndSort(
            recipes = listOf(avoided, safe),
            query = "晚餐",
            preferences = UserPreferenceDto(tastes = emptyList(), avoids = listOf("海鲜"))
        )

        assertEquals(listOf("veg"), result.map { it.id })
        assertTrue(RecipeDatabaseFilter.containsAvoidTerm(avoided, listOf("海鲜")))
    }

    @Test
    fun querySymbolsAreRemovedBeforeScoring() {
        val target = recipe("target", "abcdef", ingredients = listOf("abcdef"), tags = listOf("quick"))
        val distractor = recipe("distractor", "a", ingredients = listOf("other"), tags = listOf("premium"))

        val result = RecipeDatabaseFilter.filterAndSort(
            recipes = listOf(distractor, target),
            query = "abc\uFF0Fdef",
            preferences = UserPreferenceDto()
        )

        assertEquals("target", result.first().id)
    }

    private fun recipe(
        id: String,
        name: String,
        ingredients: List<String>,
        tags: List<String>
    ): RecipeDto {
        return RecipeDto(
            id = id,
            name = name,
            cuisine = tags.firstOrNull().orEmpty(),
            taste = tags,
            tags = tags,
            difficulty = "简单",
            cookTime = "20 分钟",
            servings = "2 人份",
            coverUrl = "",
            reason = "$name 适合晚餐。",
            ingredients = ingredients.map { IngredientDto(it, "") },
            steps = listOf("准备$name", "烹饪$name"),
            tips = "按口味调整。"
        )
    }
}
