package com.dinnerplan.shared

object RecipeDatabaseFilter {
    fun filterAndSort(
        recipes: List<RecipeDto>,
        query: String,
        preferences: UserPreferenceDto
    ): List<RecipeDto> {
        val avoidTerms = expandedTerms(preferences.avoids)
        val queryTerms = cleanTerms(query)
        val tasteTerms = expandedTerms(preferences.tastes)

        return recipes
            .filterNot { containsAny(recipeSearchText(it), avoidTerms) }
            .map { recipe -> recipe to scoreRecipe(recipe, queryTerms, tasteTerms) }
            .sortedWith(
                compareByDescending<Pair<RecipeDto, Int>> { it.second }
                    .thenByDescending { it.first.ratingStars ?: 0.0 }
                    .thenBy { it.first.name.length }
            )
            .map { it.first }
    }

    fun containsAvoidTerm(recipe: RecipeDto, avoids: List<String>): Boolean {
        return containsAny(recipeSearchText(recipe), expandedTerms(avoids))
    }

    fun containsAvoidTerm(plan: MealPlanDto, avoids: List<String>): Boolean {
        return containsAny(mealPlanSearchText(plan), expandedTerms(avoids))
    }

    fun expandedTerms(values: List<String>): List<String> {
        return values
            .flatMap(::cleanTerms)
            .flatMap(::expandTerm)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun scoreRecipe(recipe: RecipeDto, queryTerms: List<String>, tasteTerms: List<String>): Int {
        return queryTerms.sumOf { term ->
            scoreTerm(term, recipe.name, 20) +
                scoreTerm(term, recipe.ingredients.joinToString(" ") { it.name }, 12) +
                scoreTerm(term, "${recipe.taste.joinToString(" ")} ${recipe.tags.joinToString(" ")} ${recipe.cuisine}", 8) +
                scoreTerm(term, "${recipe.reason} ${recipe.steps.joinToString(" ")} ${recipe.tips}", 4)
        } + tasteTerms.sumOf { term ->
            scoreTerm(term, "${recipe.taste.joinToString(" ")} ${recipe.tags.joinToString(" ")} ${recipe.cuisine} ${recipe.name} ${recipe.reason}", 6) +
                scoreTerm(term, recipe.steps.joinToString(" "), 2)
        }
    }

    private fun recipeSearchText(recipe: RecipeDto): String {
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

    private fun mealPlanSearchText(plan: MealPlanDto): String {
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

    private fun containsAny(text: String, terms: List<String>): Boolean {
        val normalized = text.lowercase()
        return terms.any { term -> normalized.contains(term.lowercase()) }
    }

    private fun scoreTerm(term: String, text: String, weight: Int): Int {
        if (term.isBlank()) return 0
        return if (text.lowercase().contains(term.lowercase())) weight else 0
    }

    private val termSeparatorRegex = Regex("[\\s\\p{P}\\p{S}]+")

    private fun cleanTerms(value: String): List<String> {
        val compact = value.replace(termSeparatorRegex, "").trim()
        val parts = value
            .split(termSeparatorRegex)
            .map { it.trim() }
            .filter { it.isNotBlank() }
        return (listOf(compact) + parts)
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun splitTerms(value: String): List<String> {
        return value
            .split(Regex("[,，、/;；\\s]+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun expandTerm(term: String): List<String> {
        return when (term) {
            "牛羊肉" -> listOf("牛羊肉", "牛肉", "羊肉", "牛", "羊")
            "海鲜" -> listOf("海鲜", "鱼", "虾", "蟹", "贝")
            else -> listOf(term)
        }
    }
}
