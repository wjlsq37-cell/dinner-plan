package com.dinnerplan.shared

import kotlinx.serialization.Serializable

@Serializable
enum class RecommendationModeDto {
    RECIPE_SINGLE,
    RECIPE_COMBO,
    RESTAURANT
}

@Serializable
enum class CookSourceDto {
    DATABASE,
    AI_GENERATED
}

@Serializable
data class LocationDto(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val text: String? = null
)

@Serializable
data class UserPreferenceDto(
    val tastes: List<String> = emptyList(),
    val avoids: List<String> = emptyList(),
    val defaultDistanceKm: Int = 5,
    val defaultBudget: String = "medium",
    val preferQuickRecipes: Boolean = true,
    val preferOpenRestaurants: Boolean = true,
    val restaurantResultLimit: Int = 50
)

@Serializable
data class RecommendationRequest(
    val query: String,
    val mode: RecommendationModeDto,
    val location: LocationDto? = null,
    val preferences: UserPreferenceDto = UserPreferenceDto(),
    val cookSource: CookSourceDto = CookSourceDto.DATABASE,
    val requestId: String? = null,
    val broadSearch: Boolean = false
)

@Serializable
data class CancelRecommendationRequest(
    val requestId: String
)

@Serializable
data class CancelRecommendationResponse(
    val requestId: String,
    val cancelled: Boolean,
    val message: String
)

@Serializable
data class DishItemDto(
    val course: String,
    val name: String,
    val note: String,
    val badge: String,
    val recipeId: String? = null
)

@Serializable
data class RecipeDto(
    val id: String,
    val name: String,
    val cuisine: String,
    val taste: List<String>,
    val tags: List<String>,
    val difficulty: String,
    val cookTime: String,
    val servings: String,
    val coverUrl: String,
    val reason: String,
    val ingredients: List<IngredientDto>,
    val steps: List<String>,
    val tips: String,
    val ratingStars: Double? = null,
    val source: String? = null,
    val stepImageUrls: List<String> = emptyList()
)

@Serializable
data class IngredientDto(
    val name: String,
    val amount: String
)

@Serializable
data class MealPlanDto(
    val id: String,
    val title: String,
    val structure: String,
    val cookTime: String,
    val servings: String,
    val coverUrl: String,
    val tags: List<String>,
    val reason: String,
    val dishes: List<DishItemDto>,
    val shoppingList: List<String>,
    val timeline: List<String>
)

@Serializable
data class RestaurantDto(
    val id: String,
    val source: String,
    val name: String,
    val category: String,
    val tags: List<String>,
    val address: String,
    val distance: String,
    val rating: String,
    val price: String,
    val open: String,
    val phone: String,
    val coverUrl: String,
    val reason: String,
    val latitude: Double? = null,
    val longitude: Double? = null
)

@Serializable
data class CookRecommendationResponse(
    val intent: RecommendationModeDto,
    val summary: String,
    val mealPlans: List<MealPlanDto> = emptyList(),
    val recipes: List<RecipeDto> = emptyList(),
    val fallbackReason: String? = null,
    val source: CookSourceDto = CookSourceDto.DATABASE,
    val totalMatches: Int = mealPlans.size + recipes.size
)

@Serializable
data class RestaurantRecommendationResponse(
    val restaurants: List<RestaurantDto> = emptyList(),
    val locationUsed: LocationDto? = null,
    val fallbackReason: String? = null
)

@Serializable
data class HealthResponse(
    val ok: Boolean,
    val missingConfig: List<String>,
    val aiConfigured: Boolean,
    val amapConfigured: Boolean,
    val recipeCorpusReady: Boolean = false,
    val recipeCorpusCount: Long = 0,
    val recipeCorpusPath: String = ""
)
