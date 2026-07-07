package com.dinnerplan.server

import com.dinnerplan.shared.MealPlanDto
import com.dinnerplan.shared.RecipeDatabaseFilter
import com.dinnerplan.shared.RecipeDto
import com.dinnerplan.shared.RecommendationModeDto
import com.dinnerplan.shared.UserPreferenceDto

internal data class AiRecipePrompt(
    val system: String,
    val user: String
)

internal object AiRecipeGenerator {
    fun buildPrompt(
        query: String,
        mode: RecommendationModeDto,
        preferences: UserPreferenceDto,
        retryAvoidTerms: List<String> = emptyList()
    ): AiRecipePrompt {
        val modeText = if (mode == RecommendationModeDto.RECIPE_COMBO) "组合菜单" else "单道菜"
        val retryInstruction = retryAvoidTerms
            .takeIf { it.isNotEmpty() }
            ?.joinToString("、")
            ?.let { "上一次返回命中了忌口：$it。本次必须重新生成并完全避开这些词。" }
            .orEmpty()
        val system = """
            你是《吃点啥》的做饭推荐后端。只输出 JSON，不要解释。
            JSON 字段必须是：intent、summary、mealPlans、recipes。
            intent 只能是 RECIPE_SINGLE 或 RECIPE_COMBO。
            mealPlans 数组项字段：id,title,structure,cookTime,servings,coverUrl,tags,reason,dishes,shoppingList,timeline。
            dishes 数组项字段：course,name,note,badge,recipeId；badge 只能是 Meat、Veg、Soup、Staple。
            recipes 数组项字段：id,name,cuisine,taste,tags,difficulty,cookTime,servings,coverUrl,reason,ingredients,steps,tips。
            ingredients 数组项字段：name,amount。
            coverUrl 可用空字符串；不要编造餐厅，只生成做饭菜谱。
            必须避开忌口食材，不要在菜名、食材、步骤、描述、购物清单或小贴士中包含忌口。
            尽量符合偏好口味，但偏好不能覆盖忌口要求。
            $retryInstruction
        """.trimIndent()
        val user = """
            用户需求：$query
            推荐类型：$modeText
            偏好口味：${preferences.tastes.joinToString("、")}
            忌口食材：${preferences.avoids.joinToString("、")}
        """.trimIndent()
        return AiRecipePrompt(system, user)
    }

    fun filterAvoidingTerms(generation: AiCookGeneration, avoids: List<String>): AiCookGeneration {
        if (avoids.isEmpty()) return generation
        val recipes = generation.recipes.filterNot { RecipeDatabaseFilter.containsAvoidTerm(it, avoids) }
        val recipeIds = recipes.map { it.id }.filter { it.isNotBlank() }.toSet()
        val mealPlans = generation.mealPlans
            .filterNot { RecipeDatabaseFilter.containsAvoidTerm(it, avoids) }
            .map { plan -> filterMealPlanDishes(plan, recipes, recipeIds) }
            .filter { it.dishes.isNotEmpty() || it.shoppingList.isNotEmpty() }
        return generation.copy(mealPlans = mealPlans, recipes = recipes)
    }

    fun violatedAvoidTerms(generation: AiCookGeneration, avoids: List<String>): List<String> {
        val terms = RecipeDatabaseFilter.expandedTerms(avoids)
        if (terms.isEmpty()) return emptyList()
        val text = buildList {
            generation.recipes.forEach { add(recipeText(it)) }
            generation.mealPlans.forEach { add(mealPlanText(it)) }
        }.joinToString(" ").lowercase()
        return terms.filter { text.contains(it.lowercase()) }.distinct()
    }

    private fun filterMealPlanDishes(
        plan: MealPlanDto,
        safeRecipes: List<RecipeDto>,
        safeRecipeIds: Set<String>
    ): MealPlanDto {
        val recipeNames = safeRecipes.map { it.name }.toSet()
        val dishes = plan.dishes.filter { dish ->
            val recipeId = dish.recipeId
            when {
                recipeId != null && recipeId.isNotBlank() -> recipeId in safeRecipeIds
                recipeNames.isNotEmpty() -> dish.name in recipeNames || safeRecipes.none { it.name == dish.name }
                else -> true
            }
        }
        return plan.copy(
            dishes = dishes,
            shoppingList = plan.shoppingList.filter { item ->
                !RecipeDatabaseFilter.expandedTerms(emptyList()).any { item.contains(it) }
            }
        )
    }

    private fun recipeText(recipe: RecipeDto): String {
        return listOf(
            recipe.name,
            recipe.cuisine,
            recipe.taste.joinToString(" "),
            recipe.tags.joinToString(" "),
            recipe.reason,
            recipe.ingredients.joinToString(" ") { "${it.name} ${it.amount}" },
            recipe.steps.joinToString(" "),
            recipe.tips
        ).joinToString(" ")
    }

    private fun mealPlanText(plan: MealPlanDto): String {
        return listOf(
            plan.title,
            plan.structure,
            plan.tags.joinToString(" "),
            plan.reason,
            plan.dishes.joinToString(" ") { "${it.course} ${it.name} ${it.note}" },
            plan.shoppingList.joinToString(" "),
            plan.timeline.joinToString(" ")
        ).joinToString(" ")
    }
}
