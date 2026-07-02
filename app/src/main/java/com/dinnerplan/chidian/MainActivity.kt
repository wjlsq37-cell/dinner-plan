package com.dinnerplan.chidian

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dinnerplan.chidian.ui.components.FoodBottomNavigation
import com.dinnerplan.chidian.ui.screens.CookRecommendScreen
import com.dinnerplan.chidian.ui.screens.HomeScreen
import com.dinnerplan.chidian.ui.screens.MealPlanDetailScreen
import com.dinnerplan.chidian.ui.screens.NearbyRestaurantScreen
import com.dinnerplan.chidian.ui.screens.RecipeDetailScreen
import com.dinnerplan.chidian.ui.screens.RestaurantDetailScreen
import com.dinnerplan.chidian.ui.screens.SavedScreen
import com.dinnerplan.chidian.ui.screens.SettingsScreen
import com.dinnerplan.chidian.ui.screens.DeveloperSettingsScreen
import com.dinnerplan.chidian.ui.theme.ChiDianColors
import com.dinnerplan.chidian.ui.theme.ChiDianTheme
import com.dinnerplan.shared.CancelRecommendationRequest
import com.dinnerplan.shared.CookSourceDto
import com.dinnerplan.shared.DishItemDto
import com.dinnerplan.shared.LocationDto
import com.dinnerplan.shared.MealPlanDto
import com.dinnerplan.shared.RecipeDto
import com.dinnerplan.shared.RecommendationModeDto
import com.dinnerplan.shared.RecommendationRequest
import com.dinnerplan.shared.RestaurantDto
import com.dinnerplan.shared.UserPreferenceDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.chiDianDataStore by preferencesDataStore(name = "chidian_settings")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChiDianApp()
        }
    }
}

private val Tomato = Color(0xFFE2533D)
private val Muted = Color(0xFF776B61)

private val PersistenceJson = Json {
    ignoreUnknownKeys = true
}

private object SettingsKeys {
    val BackendBaseUrl = stringPreferencesKey("backend_base_url")
    val DeveloperEnabled = stringPreferencesKey("developer_enabled")
    val DeveloperAiBaseUrl = stringPreferencesKey("developer_ai_base_url")
    val DeveloperAiApiKey = stringPreferencesKey("developer_ai_api_key")
    val DeveloperAiModel = stringPreferencesKey("developer_ai_model")
    val DeveloperAmapWebKey = stringPreferencesKey("developer_amap_web_key")
    val DeveloperWanweiRecipeAppKey = stringPreferencesKey("developer_wanwei_recipe_app_key")
    val DeveloperWanweiRecipePageSize = stringPreferencesKey("developer_wanwei_recipe_page_size")
    val DeveloperMaxWaitSeconds = stringPreferencesKey("developer_max_wait_seconds")
    val CurrentLatitude = stringPreferencesKey("current_latitude")
    val CurrentLongitude = stringPreferencesKey("current_longitude")
    val LocationSource = stringPreferencesKey("location_source")
    val SavedMealIds = stringPreferencesKey("saved_meal_ids")
    val SavedRecipeIds = stringPreferencesKey("saved_recipe_ids")
    val SavedRestaurantIds = stringPreferencesKey("saved_restaurant_ids")
    val History = stringPreferencesKey("history")
    val Tastes = stringPreferencesKey("tastes")
    val Avoids = stringPreferencesKey("avoids")
    val TasteOptions = stringPreferencesKey("taste_options")
    val AvoidOptions = stringPreferencesKey("avoid_options")
    val DefaultDistance = stringPreferencesKey("default_distance")
    val PreferQuickRecipes = stringPreferencesKey("prefer_quick_recipes")
    val PreferOpenRestaurants = stringPreferencesKey("prefer_open_restaurants")
    val LocationText = stringPreferencesKey("location_text")
    val RestaurantCache = stringPreferencesKey("restaurant_cache")
    val RecipeCache = stringPreferencesKey("recipe_cache")
}

sealed interface Screen {
    data object Home : Screen
    data object CookRecommend : Screen
    data class MealPlanDetail(val id: String) : Screen
    data class RecipeDetail(val id: String) : Screen
    data object NearbyRestaurant : Screen
    data class RestaurantDetail(val id: String) : Screen
    data object Saved : Screen
    data object Settings : Screen
    data object DeveloperSettings : Screen
}

enum class RecommendMode {
    MealPlan,
    SingleRecipe
}

enum class SavedFilter {
    All,
    Restaurant,
    Cook
}

enum class RestaurantSortMode {
    Relevance,
    Distance,
    Rating
}

enum class CookSourceMode {
    Database,
    AiGenerated
}

enum class PreferenceTarget {
    Taste,
    Avoid
}

data class DishItem(
    val course: String,
    val name: String,
    val note: String,
    val badge: DishBadge,
    val recipeId: String? = null
)

enum class DishBadge {
    Meat,
    Veg,
    Soup,
    Staple
}

data class MealPlan(
    val id: String,
    val title: String,
    val structure: String,
    val cookTime: String,
    val servings: String,
    val coverUrl: String,
    val tags: List<String>,
    val reason: String,
    val dishes: List<DishItem>,
    val shoppingList: List<String>,
    val timeline: List<String>
)

data class Recipe(
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
    val ingredients: List<Pair<String, String>>,
    val steps: List<String>,
    val tips: String,
    val ratingStars: Double? = null,
    val source: String? = null
)

data class Restaurant(
    val id: String,
    val source: String = "seed",
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

data class UserPreference(
    val tastes: List<String> = listOf("微辣", "清淡"),
    val avoids: List<String> = listOf("香菜", "海鲜"),
    val defaultDistance: String = "5km",
    val preferQuickRecipes: Boolean = true,
    val preferOpenRestaurants: Boolean = true
)

sealed interface SavedItem {
    val id: String

    data class Meal(override val id: String) : SavedItem
    data class RecipeItem(override val id: String) : SavedItem
    data class RestaurantItem(override val id: String) : SavedItem
}

data class DialogState(
    val title: String,
    val message: String,
    val action: String = "知道了"
)

data class AppUiState internal constructor(
    val cookQuery: String = "两荤一素、一汤、主食、微辣",
    val restaurantQuery: String = "5km 内，微辣，人均 50 内，适合晚饭",
    val backendBaseUrl: String = "http://10.0.2.2:8080",
    internal val developerSettings: DeveloperSettings = DeveloperSettings(),
    val locationText: String = "上海人民广场",
    val currentLatitude: Double? = null,
    val currentLongitude: Double? = null,
    val locationSource: String = "",
    val recommendMode: RecommendMode = RecommendMode.MealPlan,
    val savedFilter: SavedFilter = SavedFilter.All,
    val restaurantFilterTags: Set<String> = setOf("5km", "营业中"),
    val tasteOptions: List<String> = listOf("微辣", "麻辣", "清淡", "酸甜"),
    val avoidOptions: List<String> = listOf("香菜", "海鲜", "牛羊肉", "葱蒜"),
    val preferenceEditTarget: PreferenceTarget? = null,
    val preferenceAddTarget: PreferenceTarget? = null,
    val tasteDraft: String = "",
    val avoidDraft: String = "",
    val savedMealIds: Set<String> = setOf("meal_spicy_combo"),
    val savedRecipeIds: Set<String> = setOf("recipe_beef_tomato"),
    val savedRestaurantIds: Set<String> = setOf("restaurant_noodle"),
    val history: List<SavedItem> = listOf(
        SavedItem.Meal("meal_spicy_combo"),
        SavedItem.RecipeItem("recipe_chicken_pepper"),
        SavedItem.RestaurantItem("restaurant_hunan")
    ),
    val preferences: UserPreference = UserPreference(),
    val mealPlans: List<MealPlan> = emptyList(),
    val recipes: List<Recipe> = emptyList(),
    val recipeCache: List<Recipe> = emptyList(),
    val restaurants: List<Restaurant> = emptyList(),
    val restaurantCache: List<Restaurant> = emptyList(),
    val isCookLoading: Boolean = false,
    val activeCookRequestId: String? = null,
    val activeBackendCookRequestId: String? = null,
    val cookLoadingStartedAtMillis: Long? = null,
    val cookElapsedSeconds: Int = 0,
    val isCookCanceling: Boolean = false,
    val isRestaurantLoading: Boolean = false,
    val cookFallbackReason: String? = null,
    val restaurantFallbackReason: String? = null,
    val lastRestaurantLocation: String = "",
    val cookSourceMode: CookSourceMode = CookSourceMode.Database,
    val restaurantSortMode: RestaurantSortMode = RestaurantSortMode.Relevance,
    val dialog: DialogState? = null
)

private interface AiRecommendationRepository {
    suspend fun recommendCook(
        backendBaseUrl: String,
        developerSettings: DeveloperSettings,
        query: String,
        mode: RecommendMode,
        cookSourceMode: CookSourceMode,
        preferences: UserPreference,
        requestId: String? = null
    ): CookResult

    suspend fun cancelCook(
        backendBaseUrl: String,
        developerSettings: DeveloperSettings,
        requestId: String
    ): CancelCookResult
}

private interface RestaurantRepository {
    suspend fun nearbyRestaurants(
        backendBaseUrl: String,
        developerSettings: DeveloperSettings,
        query: String,
        locationText: String,
        location: LocationDto,
        preferences: UserPreference
    ): RestaurantResult
}

data class CookResult(
    val mealPlans: List<MealPlan>,
    val recipes: List<Recipe>,
    val fallbackReason: String?
)

data class CancelCookResult(
    val cancelled: Boolean,
    val message: String?
)

data class RestaurantResult(
    val restaurants: List<Restaurant>,
    val locationUsed: String,
    val fallbackReason: String?
)

private class BackendAiRecommendationRepository(
    private val backendApiClient: BackendApiClient
) : AiRecommendationRepository {
    override suspend fun recommendCook(
        backendBaseUrl: String,
        developerSettings: DeveloperSettings,
        query: String,
        mode: RecommendMode,
        cookSourceMode: CookSourceMode,
        preferences: UserPreference,
        requestId: String?
    ): CookResult {
        return try {
            val response = backendApiClient.recommendCook(
                baseUrl = backendBaseUrl,
                request = RecommendationRequest(
                    query = query,
                    mode = mode.toDto(),
                    preferences = preferences.toDto(),
                    cookSource = cookSourceMode.toDto(),
                    requestId = requestId
                )
            )
            CookResult(
                mealPlans = response.mealPlans.map { it.toUi() },
                recipes = response.recipes.map { it.toUi() },
                fallbackReason = response.fallbackReason
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            CookResult(
                mealPlans = localMealPlans(query),
                recipes = localRecipes(query),
                fallbackReason = "后端暂时不可用，已使用本地规则推荐：${error.message ?: "未知错误"}"
            )
        }
    }

    override suspend fun cancelCook(
        backendBaseUrl: String,
        developerSettings: DeveloperSettings,
        requestId: String
    ): CancelCookResult {
        return try {
            val response = backendApiClient.cancelCookRecommendation(
                baseUrl = backendBaseUrl,
                request = CancelRecommendationRequest(requestId)
            )
            CancelCookResult(response.cancelled, response.message)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            CancelCookResult(
                cancelled = false,
                message = "本地已取消，后端取消确认失败：${error.message ?: "未知错误"}"
            )
        }
    }
}

private class BackendRestaurantRepository(
    private val backendApiClient: BackendApiClient
) : RestaurantRepository {
    override suspend fun nearbyRestaurants(
        backendBaseUrl: String,
        developerSettings: DeveloperSettings,
        query: String,
        locationText: String,
        location: LocationDto,
        preferences: UserPreference
    ): RestaurantResult {
        return runCatching {
            val response = backendApiClient.recommendRestaurants(
                baseUrl = backendBaseUrl,
                request = RecommendationRequest(
                    query = query,
                    mode = RecommendationModeDto.RESTAURANT,
                    location = location,
                    preferences = preferences.toDto()
                )
            )
            RestaurantResult(
                restaurants = response.restaurants.map { it.toUi() },
                locationUsed = response.locationUsed?.text.orEmpty().ifBlank { locationText },
                fallbackReason = response.fallbackReason
            )
        }.getOrElse { error ->
            RestaurantResult(
                restaurants = emptyList(),
                locationUsed = locationText,
                fallbackReason = "后端或高德接口暂时不可用，未生成虚假餐厅：${error.message ?: "未知错误"}"
            )
        }
    }
}

private class DirectCookRecommendationRepository(
    private val directAiApiClient: DirectAiApiClient,
    private val wanweiRecipeApiClient: WanweiRecipeApiClient
) : AiRecommendationRepository {
    override suspend fun recommendCook(
        backendBaseUrl: String,
        developerSettings: DeveloperSettings,
        query: String,
        mode: RecommendMode,
        cookSourceMode: CookSourceMode,
        preferences: UserPreference,
        requestId: String?
    ): CookResult {
        return when (cookSourceMode) {
            CookSourceMode.Database -> recommendFromWanwei(developerSettings, query, mode)
            CookSourceMode.AiGenerated -> {
                if (developerSettings.aiBaseUrl.isBlank() || developerSettings.aiApiKey.isBlank() || developerSettings.aiModel.isBlank()) {
                    return recommendFromWanwei(developerSettings, query, mode)
                        .withFallbackPrefix("开发者模式 AI 配置缺失，已改用万维易源菜谱库。")
                }
                val aiResponse = runCatching {
                    directAiApiClient.generateCookRecommendation(
                        settings = developerSettings,
                        query = query,
                        mode = mode.toDto(),
                        preferences = preferences.toDto()
                    )
                }.getOrElse { error ->
                    return recommendFromWanwei(developerSettings, query, mode)
                        .withFallbackPrefix("开发者模式 AI 请求失败：${error.message ?: "未知错误"} 已改用万维易源菜谱库。")
                }
                if (aiResponse != null) {
                    CookResult(
                        mealPlans = aiResponse.mealPlans.map { it.toUi() },
                        recipes = aiResponse.recipes.map { it.toUi() },
                        fallbackReason = aiResponse.fallbackReason
                    )
                } else {
                    recommendFromWanwei(developerSettings, query, mode)
                        .withFallbackPrefix("开发者模式 AI 请求失败，已改用万维易源菜谱库。")
                }
            }
        }
    }

    override suspend fun cancelCook(
        backendBaseUrl: String,
        developerSettings: DeveloperSettings,
        requestId: String
    ): CancelCookResult {
        return CancelCookResult(cancelled = true, message = "已取消本次开发者直连请求。")
    }

    private suspend fun recommendFromWanwei(
        developerSettings: DeveloperSettings,
        query: String,
        mode: RecommendMode
    ): CookResult {
        return if (mode == RecommendMode.SingleRecipe) {
            val result = wanweiRecipeApiClient.searchRecipes(developerSettings, query)
            CookResult(
                mealPlans = emptyList(),
                recipes = result.recipes.map { it.toUi() },
                fallbackReason = result.fallbackReason
            )
        } else {
            val slotResults = wanweiMealSlotQueries(query).map { slotQuery ->
                wanweiRecipeApiClient.searchRecipes(developerSettings, slotQuery)
            }
            val recipes = slotResults
                .flatMap { it.recipes }
                .distinctBy { it.id }
                .map { it.toUi() }
                .take(6)
            val fallbackReason = slotResults.firstOrNull { it.fallbackReason != null }?.fallbackReason
            CookResult(
                mealPlans = buildWanweiMealPlan(query, recipes)?.let(::listOf).orEmpty(),
                recipes = recipes,
                fallbackReason = fallbackReason.takeIf { recipes.isEmpty() }
            )
        }
    }
}

private class DirectRestaurantRepository(
    private val directAmapApiClient: DirectAmapApiClient
) : RestaurantRepository {
    override suspend fun nearbyRestaurants(
        backendBaseUrl: String,
        developerSettings: DeveloperSettings,
        query: String,
        locationText: String,
        location: LocationDto,
        preferences: UserPreference
    ): RestaurantResult {
        return runCatching {
            val response = directAmapApiClient.searchRestaurants(
                settings = developerSettings,
                query = query,
                locationText = locationText,
                location = location,
                preferences = preferences.toDto()
            )
            RestaurantResult(
                restaurants = response.restaurants.map { it.toUi() },
                locationUsed = response.locationUsed?.text.orEmpty().ifBlank { locationText },
                fallbackReason = response.fallbackReason
            )
        }.getOrElse { error ->
            RestaurantResult(
                restaurants = emptyList(),
                locationUsed = locationText,
                fallbackReason = "开发者直连高德失败，未生成虚假餐厅：${error.message ?: "未知错误"}"
            )
        }
    }
}

private fun localMealPlans(query: String): List<MealPlan> {
    return if (query.contains("清淡")) {
        listOf(MockData.mealPlans[1], MockData.mealPlans[0])
    } else {
        MockData.mealPlans
    }
}

private fun localRecipes(query: String): List<Recipe> {
    return if (query.contains("清淡")) {
        listOf(MockData.recipes[0], MockData.recipes[1], MockData.recipes[2])
    } else {
        listOf(MockData.recipes[1], MockData.recipes[2], MockData.recipes[0])
    }
}

private fun CookResult.withFallbackPrefix(prefix: String): CookResult {
    return copy(
        fallbackReason = listOf(prefix, fallbackReason.orEmpty())
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(" ")
    )
}

private fun wanweiMealSlotQueries(query: String): List<String> {
    val taste = when {
        query.contains("微辣") -> "微辣"
        query.contains("麻辣") -> "麻辣"
        query.contains("清淡") -> "清淡"
        else -> ""
    }
    val meatCount = if (query.contains("两荤")) 2 else 1
    val meats = listOf("家常肉菜", "鸡肉")
        .take(meatCount)
        .map { "$taste$it".trim() }
    val vegetables = if (query.contains("素")) listOf("${taste}素菜".trim()) else emptyList()
    val soup = if (query.contains("汤")) listOf("${taste}汤".trim()) else emptyList()
    val staple = if (query.contains("主食")) listOf("主食") else emptyList()
    return (meats + vegetables + soup + staple).filter { it.isNotBlank() }.ifEmpty { listOf(query) }
}

private fun buildWanweiMealPlan(query: String, recipes: List<Recipe>): MealPlan? {
    val selected = recipes.distinctBy { it.id }.take(6)
    if (selected.isEmpty()) return null
    val dishes = selected.mapIndexed { index, recipe ->
        DishItem(
            course = wanweiCourseName(index, recipe),
            name = recipe.name,
            note = recipe.reason.take(36),
            badge = wanweiDishBadge(index, recipe),
            recipeId = recipe.id
        )
    }
    val shoppingList = selected
        .flatMap { recipe -> recipe.ingredients.map { it.first } }
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .take(18)
    return MealPlan(
        id = "wanwei_meal_${query.hashCode().toString().replace("-", "n")}_${selected.size}",
        title = "${query.take(12).ifBlank { "家常" }}组合菜单",
        structure = dishes.joinToString(" · ") { it.course },
        cookTime = "约${(selected.size * 18).coerceAtLeast(35)}分钟",
        servings = "2-3 人份",
        coverUrl = selected.firstOrNull { it.coverUrl.isNotBlank() }?.coverUrl.orEmpty(),
        tags = (cookTags(query) + "万维易源").distinct().take(8),
        reason = "按你的需求从万维易源菜谱库拆分检索并组装，结果优先保留星级更高、步骤更完整的菜谱。",
        dishes = dishes,
        shoppingList = shoppingList,
        timeline = selected.mapIndexed { index, recipe -> "${index + 1}. ${recipe.name}：${recipe.cookTime}" }
    )
}

private fun wanweiCourseName(index: Int, recipe: Recipe): String {
    val text = "${recipe.name} ${recipe.tags.joinToString(" ")}"
    return when {
        text.contains("汤") || text.contains("羹") -> "汤"
        text.contains("饭") || text.contains("面") || text.contains("饼") || text.contains("主食") -> "主食"
        index >= 2 && (text.contains("素") || text.contains("青菜") || text.contains("蔬菜")) -> "素菜"
        index == 0 || index == 1 -> "荤菜"
        else -> "配菜"
    }
}

private fun wanweiDishBadge(index: Int, recipe: Recipe): DishBadge {
    val text = "${recipe.name} ${recipe.tags.joinToString(" ")}"
    return when {
        text.contains("汤") || text.contains("羹") -> DishBadge.Soup
        text.contains("饭") || text.contains("面") || text.contains("饼") || text.contains("主食") -> DishBadge.Staple
        text.contains("素") || text.contains("青菜") || text.contains("蔬菜") -> DishBadge.Veg
        index <= 1 -> DishBadge.Meat
        else -> DishBadge.Veg
    }
}

private fun UserPreference.toDto(): UserPreferenceDto {
    return UserPreferenceDto(
        tastes = tastes,
        avoids = avoids,
        defaultDistanceKm = defaultDistance.filter { it.isDigit() }.toIntOrNull() ?: 5,
        preferQuickRecipes = preferQuickRecipes,
        preferOpenRestaurants = preferOpenRestaurants
    )
}

private fun RecommendMode.toDto(): RecommendationModeDto {
    return when (this) {
        RecommendMode.MealPlan -> RecommendationModeDto.RECIPE_COMBO
        RecommendMode.SingleRecipe -> RecommendationModeDto.RECIPE_SINGLE
    }
}

private fun CookSourceMode.toDto(): CookSourceDto {
    return when (this) {
        CookSourceMode.Database -> CookSourceDto.DATABASE
        CookSourceMode.AiGenerated -> CookSourceDto.AI_GENERATED
    }
}

private fun MealPlanDto.toUi(): MealPlan {
    return MealPlan(
        id = id,
        title = title,
        structure = structure,
        cookTime = cookTime,
        servings = servings,
        coverUrl = coverUrl,
        tags = tags,
        reason = reason,
        dishes = dishes.map { it.toUi() },
        shoppingList = shoppingList,
        timeline = timeline
    )
}

private fun DishItemDto.toUi(): DishItem {
    return DishItem(
        course = course,
        name = name,
        note = note,
        badge = when (badge) {
            "Meat" -> DishBadge.Meat
            "Veg" -> DishBadge.Veg
            "Soup" -> DishBadge.Soup
            else -> DishBadge.Staple
        },
        recipeId = recipeId
    )
}

private fun RecipeDto.toUi(): Recipe {
    return Recipe(
        id = id,
        name = name,
        cuisine = cuisine,
        taste = taste,
        tags = tags,
        difficulty = difficulty,
        cookTime = cookTime,
        servings = servings,
        coverUrl = coverUrl,
        reason = reason,
        ingredients = ingredients.map { it.name to it.amount },
        steps = steps,
        tips = tips,
        ratingStars = ratingStars,
        source = source
    )
}

private fun Recipe.toDto(): RecipeDto {
    return RecipeDto(
        id = id,
        name = name,
        cuisine = cuisine,
        taste = taste,
        tags = tags,
        difficulty = difficulty,
        cookTime = cookTime,
        servings = servings,
        coverUrl = coverUrl,
        reason = reason,
        ingredients = ingredients.map { (name, amount) -> com.dinnerplan.shared.IngredientDto(name, amount) },
        steps = steps,
        tips = tips,
        ratingStars = ratingStars,
        source = source
    )
}

private fun RestaurantDto.toUi(): Restaurant {
    return Restaurant(
        id = id,
        source = source,
        name = name,
        category = category,
        tags = tags,
        address = address,
        distance = distance,
        rating = rating,
        price = price,
        open = open,
        phone = phone,
        coverUrl = coverUrl,
        reason = reason,
        latitude = latitude,
        longitude = longitude
    )
}

private fun Restaurant.toDto(): RestaurantDto {
    return RestaurantDto(
        id = id,
        source = source,
        name = name,
        category = category,
        tags = tags,
        address = address,
        distance = distance,
        rating = rating,
        price = price,
        open = open,
        phone = phone,
        coverUrl = coverUrl,
        reason = reason,
        latitude = latitude,
        longitude = longitude
    )
}

private suspend fun loadPersistedState(context: Context, current: AppUiState): AppUiState {
    val preferences = context.chiDianDataStore.data.first()
    val cachedRestaurants = decodeRestaurantCache(preferences[SettingsKeys.RestaurantCache])
    val cachedRecipes = decodeRecipeCache(preferences[SettingsKeys.RecipeCache])
    return current.copy(
        backendBaseUrl = preferences[SettingsKeys.BackendBaseUrl] ?: current.backendBaseUrl,
        developerSettings = current.developerSettings.copy(
            enabled = preferences[SettingsKeys.DeveloperEnabled]?.toBooleanStrictOrNull()
                ?: current.developerSettings.enabled,
            aiBaseUrl = preferences[SettingsKeys.DeveloperAiBaseUrl] ?: current.developerSettings.aiBaseUrl,
            aiApiKey = preferences[SettingsKeys.DeveloperAiApiKey] ?: current.developerSettings.aiApiKey,
            aiModel = preferences[SettingsKeys.DeveloperAiModel] ?: current.developerSettings.aiModel,
            amapWebKey = preferences[SettingsKeys.DeveloperAmapWebKey] ?: current.developerSettings.amapWebKey,
            wanweiRecipeAppKey = preferences[SettingsKeys.DeveloperWanweiRecipeAppKey]
                ?: current.developerSettings.wanweiRecipeAppKey,
            wanweiRecipePageSize = preferences[SettingsKeys.DeveloperWanweiRecipePageSize]?.toIntOrNull()
                ?.coerceIn(1, 50)
                ?: current.developerSettings.wanweiRecipePageSize,
            maxWaitSeconds = preferences[SettingsKeys.DeveloperMaxWaitSeconds]?.toIntOrNull()
                ?.coerceIn(10, 300)
                ?: current.developerSettings.maxWaitSeconds
        ),
        savedMealIds = preferences[SettingsKeys.SavedMealIds]?.let(::decodeSet) ?: current.savedMealIds,
        savedRecipeIds = preferences[SettingsKeys.SavedRecipeIds]?.let(::decodeSet) ?: current.savedRecipeIds,
        savedRestaurantIds = preferences[SettingsKeys.SavedRestaurantIds]?.let(::decodeSet) ?: current.savedRestaurantIds,
        history = preferences[SettingsKeys.History]?.let(::decodeHistory) ?: current.history,
        tasteOptions = preferences[SettingsKeys.TasteOptions]?.let(::decodeList) ?: current.tasteOptions,
        avoidOptions = preferences[SettingsKeys.AvoidOptions]?.let(::decodeList) ?: current.avoidOptions,
        preferences = current.preferences.copy(
            tastes = preferences[SettingsKeys.Tastes]?.let(::decodeList) ?: current.preferences.tastes,
            avoids = preferences[SettingsKeys.Avoids]?.let(::decodeList) ?: current.preferences.avoids,
            defaultDistance = preferences[SettingsKeys.DefaultDistance] ?: current.preferences.defaultDistance,
            preferQuickRecipes = preferences[SettingsKeys.PreferQuickRecipes]?.toBooleanStrictOrNull()
                ?: current.preferences.preferQuickRecipes,
            preferOpenRestaurants = preferences[SettingsKeys.PreferOpenRestaurants]?.toBooleanStrictOrNull()
                ?: current.preferences.preferOpenRestaurants
        ),
        locationText = preferences[SettingsKeys.LocationText] ?: current.locationText,
        currentLatitude = preferences[SettingsKeys.CurrentLatitude]?.toDoubleOrNull(),
        currentLongitude = preferences[SettingsKeys.CurrentLongitude]?.toDoubleOrNull(),
        locationSource = preferences[SettingsKeys.LocationSource] ?: current.locationSource,
        recipeCache = (cachedRecipes + current.recipeCache + current.recipes).distinctBy { it.id },
        restaurantCache = (cachedRestaurants + current.restaurantCache).distinctBy { it.id }
    )
}

private suspend fun persistUiState(context: Context, state: AppUiState) {
    context.chiDianDataStore.edit { preferences ->
        preferences[SettingsKeys.BackendBaseUrl] = state.backendBaseUrl
        preferences[SettingsKeys.DeveloperEnabled] = state.developerSettings.enabled.toString()
        preferences[SettingsKeys.DeveloperAiBaseUrl] = state.developerSettings.aiBaseUrl
        preferences[SettingsKeys.DeveloperAiApiKey] = state.developerSettings.aiApiKey
        preferences[SettingsKeys.DeveloperAiModel] = state.developerSettings.aiModel
        preferences[SettingsKeys.DeveloperAmapWebKey] = state.developerSettings.amapWebKey
        preferences[SettingsKeys.DeveloperWanweiRecipeAppKey] = state.developerSettings.wanweiRecipeAppKey
        preferences[SettingsKeys.DeveloperWanweiRecipePageSize] = state.developerSettings.safePageSize.toString()
        preferences[SettingsKeys.DeveloperMaxWaitSeconds] = state.developerSettings.safeMaxWaitSeconds.toString()
        preferences[SettingsKeys.SavedMealIds] = encodeList(state.savedMealIds.toList())
        preferences[SettingsKeys.SavedRecipeIds] = encodeList(state.savedRecipeIds.toList())
        preferences[SettingsKeys.SavedRestaurantIds] = encodeList(state.savedRestaurantIds.toList())
        preferences[SettingsKeys.History] = encodeHistory(state.history)
        preferences[SettingsKeys.Tastes] = encodeList(state.preferences.tastes)
        preferences[SettingsKeys.Avoids] = encodeList(state.preferences.avoids)
        preferences[SettingsKeys.TasteOptions] = encodeList(state.tasteOptions)
        preferences[SettingsKeys.AvoidOptions] = encodeList(state.avoidOptions)
        preferences[SettingsKeys.DefaultDistance] = state.preferences.defaultDistance
        preferences[SettingsKeys.PreferQuickRecipes] = state.preferences.preferQuickRecipes.toString()
        preferences[SettingsKeys.PreferOpenRestaurants] = state.preferences.preferOpenRestaurants.toString()
        preferences[SettingsKeys.LocationText] = state.locationText
        state.currentLatitude?.let { preferences[SettingsKeys.CurrentLatitude] = it.toString() }
            ?: preferences.remove(SettingsKeys.CurrentLatitude)
        state.currentLongitude?.let { preferences[SettingsKeys.CurrentLongitude] = it.toString() }
            ?: preferences.remove(SettingsKeys.CurrentLongitude)
        preferences[SettingsKeys.LocationSource] = state.locationSource
        preferences[SettingsKeys.RecipeCache] = encodeRecipeCache((state.recipeCache + state.recipes).distinctBy { it.id }.take(80))
        preferences[SettingsKeys.RestaurantCache] = encodeRestaurantCache(state.restaurantCache)
    }
}

private fun decodeList(value: String?): List<String> {
    return value.orEmpty()
        .split('\u001E')
        .map { it.trim() }
        .filter { it.isNotBlank() }
}

private fun decodeSet(value: String?): Set<String> = decodeList(value).toSet()

private fun encodeList(values: List<String>): String {
    return values.filter { it.isNotBlank() }.distinct().joinToString("\u001E")
}

private fun decodeHistory(value: String?): List<SavedItem> {
    return decodeList(value).mapNotNull { raw ->
        val type = raw.substringBefore(":")
        val id = raw.substringAfter(":", "")
        when {
            id.isBlank() -> null
            type == "meal" -> SavedItem.Meal(id)
            type == "recipe" -> SavedItem.RecipeItem(id)
            type == "restaurant" -> SavedItem.RestaurantItem(id)
            else -> null
        }
    }
}

private fun encodeHistory(values: List<SavedItem>): String {
    return encodeList(
        values.map { item ->
            when (item) {
                is SavedItem.Meal -> "meal:${item.id}"
                is SavedItem.RecipeItem -> "recipe:${item.id}"
                is SavedItem.RestaurantItem -> "restaurant:${item.id}"
            }
        }
    )
}

private fun decodeRecipeCache(value: String?): List<Recipe> {
    if (value.isNullOrBlank()) return emptyList()
    return runCatching {
        PersistenceJson.decodeFromString(ListSerializer(RecipeDto.serializer()), value)
            .map { it.toUi() }
    }.getOrDefault(emptyList())
}

private fun encodeRecipeCache(recipes: List<Recipe>): String {
    return PersistenceJson.encodeToString(
        ListSerializer(RecipeDto.serializer()),
        recipes.distinctBy { it.id }.map { it.toDto() }
    )
}

private fun decodeRestaurantCache(value: String?): List<Restaurant> {
    if (value.isNullOrBlank()) return emptyList()
    return runCatching {
        PersistenceJson.decodeFromString(ListSerializer(RestaurantDto.serializer()), value)
            .map { it.toUi() }
    }.getOrDefault(emptyList())
}

private fun encodeRestaurantCache(restaurants: List<Restaurant>): String {
    return PersistenceJson.encodeToString(
        ListSerializer(RestaurantDto.serializer()),
        restaurants.distinctBy { it.id }.map { it.toDto() }
    )
}

private fun findMealPlan(id: String, state: AppUiState): MealPlan? {
    return (state.mealPlans + MockData.mealPlans).firstOrNull { it.id == id }
}

private fun findRecipe(id: String, state: AppUiState): Recipe? {
    return (state.recipes + state.recipeCache + MockData.recipes)
        .distinctBy { it.id }
        .firstOrNull { it.id == id }
}

private fun findRestaurant(id: String, state: AppUiState): Restaurant? {
    return (state.restaurants + state.restaurantCache + MockData.restaurants)
        .distinctBy { it.id }
        .firstOrNull { it.id == id }
}

object MockData {
    val recipes = listOf(
        Recipe(
            id = "recipe_beef_tomato",
            name = "番茄肥牛汤",
            cuisine = "家常菜",
            taste = listOf("酸甜", "微辣", "暖胃"),
            tags = listOf("25 分钟", "新手友好", "少油"),
            difficulty = "简单",
            cookTime = "25 分钟",
            servings = "2 人份",
            coverUrl = "https://images.unsplash.com/photo-1582878826629-29b7ad1cdc43?auto=format&fit=crop&w=900&q=80",
            reason = "酸甜开胃，肥牛熟得快，适合想吃热乎但不想大动干戈的晚餐。",
            ingredients = listOf("肥牛卷" to "200g", "番茄" to "2 个", "金针菇" to "1 把", "蒜末" to "适量", "番茄酱" to "1 勺", "盐" to "适量"),
            steps = listOf(
                "番茄去皮切块，金针菇洗净，肥牛提前焯水去浮沫。",
                "锅中少油炒香蒜末，加入番茄块和番茄酱，炒到出沙。",
                "加入热水煮开，放入金针菇煮 3 分钟，再放肥牛。",
                "加盐调味，喜欢微辣可以加少量小米辣，出锅前撒葱花。"
            ),
            tips = "肥牛先焯水，汤会更清爽；番茄炒出沙后再加水，味道更浓。"
        ),
        Recipe(
            id = "recipe_chicken_pepper",
            name = "青椒鸡丁",
            cuisine = "川湘家常",
            taste = listOf("鲜香", "微辣"),
            tags = listOf("20 分钟", "下饭", "高蛋白"),
            difficulty = "简单",
            cookTime = "20 分钟",
            servings = "2 人份",
            coverUrl = "https://images.unsplash.com/photo-1563379926898-05f4575a45d8?auto=format&fit=crop&w=900&q=80",
            reason = "鸡肉和青椒都容易熟，微辣下饭，油量可控，适合工作日晚餐。",
            ingredients = listOf("鸡胸肉" to "250g", "青椒" to "2 个", "生抽" to "1 勺", "淀粉" to "半勺", "蒜片" to "适量", "黑胡椒" to "少许"),
            steps = listOf(
                "鸡胸肉切丁，加生抽、黑胡椒和淀粉抓匀腌 8 分钟。",
                "青椒切块，锅中少油先炒鸡丁至变色。",
                "加入蒜片和青椒，大火翻炒 2 分钟。",
                "补少量盐和生抽，翻匀后立即出锅，保持青椒爽脆。"
            ),
            tips = "鸡丁不要炒太久，表面变白后再回锅翻炒，口感更嫩。"
        ),
        Recipe(
            id = "recipe_sour_spicy_potato",
            name = "酸辣土豆丝",
            cuisine = "家常菜",
            taste = listOf("酸辣", "爽脆"),
            tags = listOf("15 分钟", "低成本", "快手菜"),
            difficulty = "入门",
            cookTime = "15 分钟",
            servings = "2 人份",
            coverUrl = "https://images.unsplash.com/photo-1604908176997-125f25cc6f3d?auto=format&fit=crop&w=900&q=80",
            reason = "材料简单、出菜很快，酸辣味明显，特别适合没想法时兜底。",
            ingredients = listOf("土豆" to "2 个", "干辣椒" to "3 个", "白醋" to "1 勺", "蒜末" to "适量", "盐" to "适量", "青椒" to "半个"),
            steps = listOf(
                "土豆切丝后用清水冲洗两遍，去掉表面淀粉。",
                "热锅少油炒香蒜末和干辣椒。",
                "下土豆丝大火快炒，沿锅边淋白醋。",
                "加盐调味，土豆丝断生后立刻出锅。"
            ),
            tips = "土豆丝冲水和大火快炒是爽脆的关键。"
        )
    )

    val mealPlans = listOf(
        MealPlan(
            id = "meal_spicy_combo",
            title = "微辣两荤一素一汤晚餐",
            structure = "两荤一素 · 一汤 · 主食",
            cookTime = "约 45 分钟",
            servings = "2-3 人份",
            coverUrl = "https://images.unsplash.com/photo-1559847844-5315695dadae?auto=format&fit=crop&w=900&q=80",
            tags = listOf("两荤一素", "一汤", "主食", "微辣"),
            reason = "按“两荤一素、一汤、主食、微辣”拆解，荤菜负责下饭和蛋白质，素菜负责清爽解腻，汤和米饭补完整晚餐结构。",
            dishes = listOf(
                DishItem("荤菜", "青椒鸡丁", "微辣高蛋白，20 分钟", DishBadge.Meat, "recipe_chicken_pepper"),
                DishItem("荤菜", "蒜香小炒肉", "下饭肉菜，少油快炒", DishBadge.Meat),
                DishItem("素菜", "酸辣土豆丝", "爽脆开胃，15 分钟", DishBadge.Veg, "recipe_sour_spicy_potato"),
                DishItem("汤", "番茄豆腐汤", "清爽收尾，少油暖胃", DishBadge.Soup),
                DishItem("主食", "米饭 / 杂粮饭", "提前煮，配微辣菜更稳", DishBadge.Staple)
            ),
            shoppingList = listOf("鸡胸肉 250g", "五花肉 150g", "土豆 2 个", "青椒 3 个", "番茄 2 个", "豆腐 1 盒", "米饭 2-3 人份", "蒜、生抽、白醋、小米辣"),
            timeline = listOf(
                "先淘米煮饭，同时切配鸡肉、土豆丝、青椒和番茄。",
                "土豆丝泡水，鸡丁腌制 8 分钟，汤锅先煮番茄豆腐汤底。",
                "先炒酸辣土豆丝，再炒青椒鸡丁和小炒肉，最后给汤调味。",
                "所有热菜集中在最后 20 分钟出锅，口感更好。"
            )
        ),
        MealPlan(
            id = "meal_light_combo",
            title = "清淡快手一人晚餐",
            structure = "一荤一素 · 一汤 · 主食",
            cookTime = "约 30 分钟",
            servings = "1-2 人份",
            coverUrl = "https://images.unsplash.com/photo-1512058564366-18510be2db19?auto=format&fit=crop&w=900&q=80",
            tags = listOf("清淡", "快手", "一人食", "少油"),
            reason = "适合不想吃太油时使用，保留蛋白质、蔬菜、热汤和主食，整体负担更轻。",
            dishes = listOf(
                DishItem("荤菜", "番茄肥牛汤", "汤菜合一，25 分钟", DishBadge.Meat, "recipe_beef_tomato"),
                DishItem("素菜", "清炒时蔬", "少油补蔬菜", DishBadge.Veg),
                DishItem("汤", "紫菜蛋花汤", "5 分钟补汤水", DishBadge.Soup),
                DishItem("主食", "米饭 / 面条", "按饱腹感选择", DishBadge.Staple)
            ),
            shoppingList = listOf("肥牛卷 200g", "番茄 2 个", "青菜 1 把", "鸡蛋 2 个", "紫菜 少许", "米饭或挂面", "盐、生抽、蒜"),
            timeline = listOf(
                "先准备主食，再处理番茄、青菜和鸡蛋。",
                "番茄肥牛汤先煮，等待时清炒时蔬。",
                "最后 5 分钟冲紫菜蛋花汤，全部一起上桌。"
            )
        )
    )

    val restaurants = listOf(
        Restaurant(
            id = "restaurant_noodle",
            name = "巷口牛肉面",
            category = "面馆",
            tags = listOf("牛肉面", "单人友好", "快餐"),
            address = "人民大道 88 号 B1 层",
            distance = "850m",
            rating = "4.6",
            price = "人均 ¥28",
            open = "营业中 · 10:00-22:00",
            phone = "021-0000 8828",
            coverUrl = "https://images.unsplash.com/photo-1552611052-33e04de081de?auto=format&fit=crop&w=900&q=80",
            reason = "距离近、出餐快，主打牛肉面和小菜，适合一个人快速解决晚饭。"
        ),
        Restaurant(
            id = "restaurant_hunan",
            name = "小灶湘味馆",
            category = "湘菜",
            tags = listOf("微辣", "下饭菜", "朋友聚餐"),
            address = "南京西路 126 号 2 楼",
            distance = "1.7km",
            rating = "4.7",
            price = "人均 ¥56",
            open = "营业中 · 11:00-21:30",
            phone = "021-0000 5620",
            coverUrl = "https://images.unsplash.com/photo-1555396273-367ea4eb4db5?auto=format&fit=crop&w=900&q=80",
            reason = "口味更接近微辣下饭，评分高，适合想吃热菜但不想走太远。"
        ),
        Restaurant(
            id = "restaurant_canton",
            name = "明炉烧味茶餐厅",
            category = "粤菜",
            tags = listOf("清淡", "烧味", "汤粉"),
            address = "福州路 39 号",
            distance = "2.4km",
            rating = "4.5",
            price = "人均 ¥48",
            open = "营业中 · 09:30-22:00",
            phone = "021-0000 3948",
            coverUrl = "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?auto=format&fit=crop&w=900&q=80",
            reason = "清淡选择多，烧味饭和汤粉都适合晚餐，适合不想吃太油的时候。"
        )
    )
}

@Composable
private fun ChiDianApp() {
    val context = LocalContext.current
    val backendApiClient = remember { BackendApiClient() }
    val backendAiRepository = remember { BackendAiRecommendationRepository(backendApiClient) }
    val directCookRepository = remember {
        DirectCookRecommendationRepository(
            directAiApiClient = DirectAiApiClient(),
            wanweiRecipeApiClient = WanweiRecipeApiClient()
        )
    }
    val backendRestaurantRepository = remember { BackendRestaurantRepository(backendApiClient) }
    val directRestaurantRepository = remember { DirectRestaurantRepository(DirectAmapApiClient()) }
    val scope = rememberCoroutineScope()
    val backStack = remember { mutableStateListOf<Screen>() }
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    var uiState by remember {
        mutableStateOf(
            AppUiState(
                mealPlans = MockData.mealPlans,
                recipes = MockData.recipes,
                recipeCache = MockData.recipes,
                restaurantCache = MockData.restaurants
            )
        )
    }
    var settingsLoaded by remember { mutableStateOf(false) }
    var cookRecommendationJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(Unit) {
        uiState = loadPersistedState(context, uiState)
        settingsLoaded = true
    }

    LaunchedEffect(uiState.activeCookRequestId, uiState.cookLoadingStartedAtMillis) {
        val requestId = uiState.activeCookRequestId ?: return@LaunchedEffect
        while (true) {
            delay(1_000)
            val startedAt = uiState.cookLoadingStartedAtMillis ?: break
            if (!uiState.isCookLoading || uiState.activeCookRequestId != requestId) break
            val elapsedSeconds = ((System.currentTimeMillis() - startedAt) / 1_000).toInt().coerceAtLeast(0)
            uiState = uiState.copy(cookElapsedSeconds = elapsedSeconds)
        }
    }

    LaunchedEffect(
        settingsLoaded,
        uiState.backendBaseUrl,
        uiState.developerSettings,
        uiState.savedMealIds,
        uiState.savedRecipeIds,
        uiState.savedRestaurantIds,
        uiState.history,
        uiState.preferences,
        uiState.tasteOptions,
        uiState.avoidOptions,
        uiState.locationText,
        uiState.currentLatitude,
        uiState.currentLongitude,
        uiState.locationSource,
        uiState.recipeCache,
        uiState.restaurantCache
    ) {
        if (settingsLoaded) {
            persistUiState(context, uiState)
        }
    }

    fun navigate(screen: Screen) {
        if (currentScreen != screen) {
            backStack.add(currentScreen)
            currentScreen = screen
        }
    }

    fun topLevel(screen: Screen) {
        backStack.clear()
        currentScreen = screen
    }

    fun goBack() {
        currentScreen = if (backStack.isNotEmpty()) {
            backStack.removeAt(backStack.lastIndex)
        } else {
            Screen.Home
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun toast(message: String) = Unit

    fun addHistory(item: SavedItem) {
        uiState = uiState.copy(history = (listOf(item) + uiState.history.filterNot { it == item }).take(10))
    }

    fun toggleMeal(id: String) {
        val next = toggleSet(uiState.savedMealIds, id)
        uiState = uiState.copy(savedMealIds = next)
        toast(if (id in next) "整套菜单已收藏" else "已取消收藏整套菜单")
    }

    fun toggleRecipe(id: String) {
        val next = toggleSet(uiState.savedRecipeIds, id)
        val recipe = findRecipe(id, uiState)
        val cache = if (recipe == null) {
            uiState.recipeCache
        } else {
            (listOf(recipe) + uiState.recipeCache).distinctBy { it.id }
        }
        uiState = uiState.copy(savedRecipeIds = next, recipeCache = cache)
        toast(if (id in next) "菜谱已收藏" else "已取消收藏菜谱")
    }

    fun toggleRestaurant(id: String) {
        val next = toggleSet(uiState.savedRestaurantIds, id)
        val restaurant = findRestaurant(id, uiState)
        val cache = if (restaurant == null) {
            uiState.restaurantCache
        } else {
            (listOf(restaurant) + uiState.restaurantCache).distinctBy { it.id }
        }
        uiState = uiState.copy(savedRestaurantIds = next, restaurantCache = cache)
        toast(if (id in next) "餐厅已收藏" else "已取消收藏餐厅")
    }

    fun showDialog(title: String, message: String, action: String = "知道了") {
        uiState = uiState.copy(dialog = DialogState(title, message, action))
    }

    fun refreshCookRecommendations() {
        val snapshot = uiState
        val previousBackendRequestId = snapshot.activeBackendCookRequestId
        if (previousBackendRequestId != null) {
            cookRecommendationJob?.cancel()
            scope.launch {
                backendAiRepository.cancelCook(snapshot.backendBaseUrl, snapshot.developerSettings, previousBackendRequestId)
            }
        } else {
            cookRecommendationJob?.cancel()
        }
        val requestId = UUID.randomUUID().toString()
        val backendRequestId = if (!snapshot.developerSettings.enabled && snapshot.cookSourceMode == CookSourceMode.AiGenerated) {
            requestId
        } else {
            null
        }
        uiState = uiState.copy(
            isCookLoading = true,
            activeCookRequestId = requestId,
            activeBackendCookRequestId = backendRequestId,
            cookLoadingStartedAtMillis = if (snapshot.cookSourceMode == CookSourceMode.AiGenerated) System.currentTimeMillis() else null,
            cookElapsedSeconds = 0,
            isCookCanceling = false,
            cookFallbackReason = null
        )
        cookRecommendationJob = scope.launch {
            try {
                val repository = if (snapshot.developerSettings.enabled) directCookRepository else backendAiRepository
                val result = repository.recommendCook(
                    backendBaseUrl = snapshot.backendBaseUrl,
                    developerSettings = snapshot.developerSettings,
                    query = snapshot.cookQuery,
                    mode = snapshot.recommendMode,
                    cookSourceMode = snapshot.cookSourceMode,
                    preferences = snapshot.preferences,
                    requestId = backendRequestId
                )
                if (uiState.activeCookRequestId != requestId) {
                    return@launch
                }
                val hasBackendResult = result.mealPlans.isNotEmpty() || result.recipes.isNotEmpty()
                val nextRecipes = if (hasBackendResult) result.recipes else localRecipes(snapshot.cookQuery)
                val nextMealPlans = if (hasBackendResult) result.mealPlans else localMealPlans(snapshot.cookQuery)
                val nextMode = when {
                    snapshot.recommendMode == RecommendMode.MealPlan && nextMealPlans.isNotEmpty() -> RecommendMode.MealPlan
                    nextRecipes.isNotEmpty() -> RecommendMode.SingleRecipe
                    else -> snapshot.recommendMode
                }
                uiState = uiState.copy(
                    isCookLoading = false,
                    activeCookRequestId = null,
                    activeBackendCookRequestId = null,
                    cookLoadingStartedAtMillis = null,
                    cookElapsedSeconds = 0,
                    isCookCanceling = false,
                    recommendMode = nextMode,
                    mealPlans = nextMealPlans,
                    recipes = nextRecipes,
                    recipeCache = (nextRecipes + snapshot.recipeCache).distinctBy { it.id }.take(80),
                    cookFallbackReason = result.fallbackReason
                )
                toast(result.fallbackReason ?: if (snapshot.cookSourceMode == CookSourceMode.Database) "已从本地菜谱库搜索" else "已生成做饭推荐")
            } catch (error: CancellationException) {
                if (uiState.activeCookRequestId == requestId) {
                    uiState = uiState.copy(
                        isCookLoading = false,
                        activeCookRequestId = null,
                        activeBackendCookRequestId = null,
                        cookLoadingStartedAtMillis = null,
                        cookElapsedSeconds = 0,
                        isCookCanceling = false
                    )
                }
            }
        }
    }

    fun cancelCookRecommendations() {
        val requestId = uiState.activeBackendCookRequestId
        val backendBaseUrl = uiState.backendBaseUrl
        cookRecommendationJob?.cancel()
        cookRecommendationJob = null
        uiState = uiState.copy(
            isCookLoading = false,
            activeCookRequestId = null,
            activeBackendCookRequestId = null,
            cookLoadingStartedAtMillis = null,
            cookElapsedSeconds = 0,
            isCookCanceling = false
        )
        if (requestId == null) {
            toast("已取消本次菜谱搜索")
            return
        }
        scope.launch {
            val result = backendAiRepository.cancelCook(backendBaseUrl, uiState.developerSettings, requestId)
            toast(
                when {
                    result.cancelled -> "已取消本次 AI 菜谱制作"
                    result.message.isNullOrBlank() -> "本地已取消，后端未找到正在制作的任务"
                    else -> result.message
                }
            )
        }
    }

    fun refreshRestaurants() {
        scope.launch {
            val snapshot = uiState
            val snapshotLatitude = snapshot.currentLatitude
            val snapshotLongitude = snapshot.currentLongitude
            if (snapshotLatitude != null && snapshotLongitude != null &&
                !isLikelyAmapSearchCoordinate(snapshotLatitude, snapshotLongitude)
            ) {
                uiState = uiState.copy(
                    isRestaurantLoading = false,
                    restaurants = emptyList(),
                    restaurantFallbackReason = unsupportedAmapLocationMessage(snapshotLatitude, snapshotLongitude),
                    lastRestaurantLocation = snapshot.locationText
                )
                return@launch
            }
            uiState = uiState.copy(isRestaurantLoading = true, restaurantFallbackReason = null)
            val repository = if (snapshot.developerSettings.enabled) directRestaurantRepository else backendRestaurantRepository
            val location = restaurantLocationForSearch(
                locationText = snapshot.locationText,
                latitude = snapshot.currentLatitude,
                longitude = snapshot.currentLongitude
            )
            val result = repository.nearbyRestaurants(
                backendBaseUrl = snapshot.backendBaseUrl,
                developerSettings = snapshot.developerSettings,
                query = snapshot.restaurantQuery,
                locationText = snapshot.locationText,
                location = location,
                preferences = snapshot.preferences
            )
            uiState = uiState.copy(
                isRestaurantLoading = false,
                restaurants = result.restaurants,
                restaurantCache = (result.restaurants + snapshot.restaurantCache).distinctBy { it.id },
                restaurantFallbackReason = result.fallbackReason,
                lastRestaurantLocation = result.locationUsed
            )
            toast(result.fallbackReason ?: "已从高德地图获取真实餐厅")
        }
    }

    fun hasRuntimeLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    fun locateAndRefreshRestaurants() {
        scope.launch {
            toast("正在获取当前位置")
            when (val result = getCurrentDeviceLocation(context)) {
                is DeviceLocationResult.Found -> {
                    val label = resolveDeviceLocationLabel(context, result.latitude, result.longitude)
                    uiState = uiState.copy(
                        locationText = label,
                        currentLatitude = result.latitude,
                        currentLongitude = result.longitude,
                        locationSource = result.source,
                        lastRestaurantLocation = label
                    )
                    if (!isLikelyAmapSearchCoordinate(result.latitude, result.longitude)) {
                        uiState = uiState.copy(
                            restaurants = emptyList(),
                            restaurantFallbackReason = unsupportedAmapLocationMessage(result.latitude, result.longitude),
                            lastRestaurantLocation = label
                        )
                        toast("当前位置不在高德周边 POI 覆盖范围内")
                        return@launch
                    }
                    toast("已获取当前位置，正在搜索附近餐厅")
                    refreshRestaurants()
                }
                is DeviceLocationResult.Unavailable -> {
                    showDialog("定位暂时不可用", "${result.message}\n你仍然可以手动输入城市、商圈或地标搜索。")
                }
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) {
            locateAndRefreshRestaurants()
        } else {
            showDialog("定位权限未开启", "未获得定位权限。你仍然可以手动输入城市、商圈或地标搜索。")
        }
    }

    fun requestCurrentLocation() {
        if (hasRuntimeLocationPermission()) {
            locateAndRefreshRestaurants()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    fun openRestaurantNavigation(restaurant: Restaurant) {
        val result = openMapNavigation(
            context = context,
            request = RestaurantNavigationRequest(
                name = restaurant.name,
                address = restaurant.address,
                latitude = restaurant.latitude,
                longitude = restaurant.longitude
            )
        )
        when (result) {
            MapNavigationResult.Opened -> toast("已打开地图")
            MapNavigationResult.MissingDestination -> showDialog("无法导航", "这个餐厅缺少经纬度和地址，暂时无法打开地图。")
            is MapNavigationResult.Failed -> showDialog("无法打开地图", result.message)
        }
    }

    BackHandler(enabled = currentScreen != Screen.Home || backStack.isNotEmpty()) {
        goBack()
    }

    ChiDianTheme {
        Scaffold(
            containerColor = ChiDianColors.Canvas,
            bottomBar = {
                FoodBottomNavigation(
                    selected = currentScreen,
                    onHome = { topLevel(Screen.Home) },
                    onNearby = { topLevel(Screen.NearbyRestaurant) },
                    onSaved = { topLevel(Screen.Saved) },
                    onSettings = { topLevel(Screen.Settings) }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ChiDianColors.Canvas)
                    .padding(innerPadding)
            ) {
                when (val screen = currentScreen) {
                    Screen.Home -> HomeScreen(
                        onCookSearch = {
                            uiState = uiState.copy(recommendMode = RecommendMode.MealPlan)
                            navigate(Screen.CookRecommend)
                            toast("进入自己做")
                        },
                        onRestaurantSearch = {
                            navigate(Screen.NearbyRestaurant)
                            toast("已按附近吃需求搜索餐厅")
                        },
                        onSettings = { topLevel(Screen.Settings) },
                        onMeal = {
                            addHistory(SavedItem.Meal(it))
                            navigate(Screen.MealPlanDetail(it))
                        },
                        onRestaurant = {
                            addHistory(SavedItem.RestaurantItem(it))
                            navigate(Screen.RestaurantDetail(it))
                        }
                    )

                    Screen.CookRecommend -> CookRecommendScreen(
                        state = uiState,
                        mealPlans = uiState.mealPlans,
                        recipes = uiState.recipes,
                        onStateChange = { uiState = it },
                        onBack = ::goBack,
                        onMeal = {
                            addHistory(SavedItem.Meal(it))
                            navigate(Screen.MealPlanDetail(it))
                        },
                        onRecipe = {
                            addHistory(SavedItem.RecipeItem(it))
                            navigate(Screen.RecipeDetail(it))
                        },
                        onToggleMeal = ::toggleMeal,
                        onToggleRecipe = ::toggleRecipe,
                        onSearch = ::refreshCookRecommendations,
                        onCancelSearch = ::cancelCookRecommendations,
                        onReroll = {
                            uiState = uiState.copy(
                                recommendMode = if (uiState.recommendMode == RecommendMode.MealPlan) {
                                    RecommendMode.SingleRecipe
                                } else {
                                    RecommendMode.MealPlan
                                }
                            )
                            toast("已切换推荐类型")
                        }
                    )

                    is Screen.MealPlanDetail -> MealPlanDetailScreen(
                        plan = findMealPlan(screen.id, uiState) ?: MockData.mealPlans.first(),
                        isSaved = screen.id in uiState.savedMealIds,
                        onBack = ::goBack,
                        onToggleSave = { mealId -> toggleMeal(mealId) },
                        onRecipe = {
                            addHistory(SavedItem.RecipeItem(it))
                            navigate(Screen.RecipeDetail(it))
                        }
                    )

                    is Screen.RecipeDetail -> RecipeDetailScreen(
                        recipe = findRecipe(screen.id, uiState) ?: MockData.recipes.first(),
                        isSaved = screen.id in uiState.savedRecipeIds,
                        onBack = ::goBack,
                        onToggleSave = { recipeId -> toggleRecipe(recipeId) }
                    )

                    Screen.NearbyRestaurant -> NearbyRestaurantScreen(
                        state = uiState,
                        restaurants = uiState.restaurants,
                        onStateChange = { uiState = it },
                        onBack = ::goBack,
                        onRestaurant = {
                            addHistory(SavedItem.RestaurantItem(it))
                            navigate(Screen.RestaurantDetail(it))
                        },
                        onToggleRestaurant = ::toggleRestaurant,
                        onLocateIssue = ::requestCurrentLocation,
                        onSearch = ::refreshRestaurants
                    )

                    is Screen.RestaurantDetail -> RestaurantDetailScreen(
                        restaurant = findRestaurant(screen.id, uiState) ?: MockData.restaurants.first(),
                        isSaved = screen.id in uiState.savedRestaurantIds,
                        onBack = ::goBack,
                        onToggleSave = { restaurantId -> toggleRestaurant(restaurantId) },
                        onNavigate = { openRestaurantNavigation(findRestaurant(screen.id, uiState) ?: MockData.restaurants.first()) }
                    )

                    Screen.Saved -> SavedScreen(
                        state = uiState,
                        onStateChange = { uiState = it },
                        onBack = { topLevel(Screen.Home) },
                        onMeal = {
                            addHistory(SavedItem.Meal(it))
                            navigate(Screen.MealPlanDetail(it))
                        },
                        onRecipe = {
                            addHistory(SavedItem.RecipeItem(it))
                            navigate(Screen.RecipeDetail(it))
                        },
                        onRestaurant = {
                            addHistory(SavedItem.RestaurantItem(it))
                            navigate(Screen.RestaurantDetail(it))
                        },
                        onInfo = {
                            showDialog("收藏会保存在本地", "收藏、历史、偏好和开发者设置已通过 DataStore 保存在本机。")
                        }
                    )

                    Screen.Settings -> SettingsScreen(
                        state = uiState,
                        onStateChange = { uiState = it },
                        onBack = { topLevel(Screen.Home) },
                        onSave = { toast("偏好已保存") },
                        onDeveloperSettings = { navigate(Screen.DeveloperSettings) }
                    )

                    Screen.DeveloperSettings -> DeveloperSettingsScreen(
                        backendBaseUrl = uiState.backendBaseUrl,
                        settings = uiState.developerSettings,
                        onBackendBaseUrlChange = { uiState = uiState.copy(backendBaseUrl = it) },
                        onSettingsChange = { uiState = uiState.copy(developerSettings = it) },
                        onBack = ::goBack,
                        onSave = { toast("开发者设置已保存") }
                    )
                }
            }
        }

        uiState.dialog?.let { dialog ->
            AlertDialog(
                onDismissRequest = { uiState = uiState.copy(dialog = null) },
                title = { Text(dialog.title, fontWeight = FontWeight.Bold) },
                text = { Text(dialog.message, color = Muted) },
                confirmButton = {
                    Button(
                        onClick = { uiState = uiState.copy(dialog = null) },
                        colors = ButtonDefaults.buttonColors(containerColor = Tomato)
                    ) {
                        Text(dialog.action)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { uiState = uiState.copy(dialog = null) }) {
                        Text("关闭")
                    }
                }
            )
        }
    }
}

fun toggleSet(source: Set<String>, id: String): Set<String> {
    return if (id in source) source - id else source + id
}

fun toggleList(source: List<String>, value: String): List<String> {
    return if (value in source) source - value else source + value
}

fun appendUnique(source: List<String>, value: String): List<String> {
    return if (value in source) source else source + value
}

fun sortRestaurants(restaurants: List<Restaurant>, mode: RestaurantSortMode): List<Restaurant> {
    return when (mode) {
        RestaurantSortMode.Relevance -> restaurants
        RestaurantSortMode.Distance -> restaurants.sortedBy { parseDistanceMeters(it.distance) ?: Double.MAX_VALUE }
        RestaurantSortMode.Rating -> restaurants.sortedByDescending { parseRatingScore(it.rating) ?: -1.0 }
    }
}

fun sortModeLabel(mode: RestaurantSortMode): String {
    return when (mode) {
        RestaurantSortMode.Relevance -> "按相关度"
        RestaurantSortMode.Distance -> "离你最近"
        RestaurantSortMode.Rating -> "评分最高"
    }
}

fun formatElapsedTime(totalSeconds: Int): String {
    val safeSeconds = totalSeconds.coerceAtLeast(0)
    val minutes = safeSeconds / 60
    val seconds = safeSeconds % 60
    return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
}

fun recipeMetaLine(recipe: Recipe): String {
    return buildList {
        recipe.ratingStars?.let { add("${"%.1f".format(it)} 星") }
        add(recipe.cuisine)
        add(recipe.cookTime)
        add(recipe.difficulty)
    }.filter { it.isNotBlank() }.joinToString(" · ")
}

fun parseDistanceMeters(distance: String): Double? {
    val number = Regex("""\d+(\.\d+)?""").find(distance)?.value?.toDoubleOrNull() ?: return null
    val normalized = distance.lowercase()
    return if (normalized.contains("km") || normalized.contains("公里")) {
        number * 1000
    } else {
        number
    }
}

fun parseRatingScore(rating: String): Double? {
    return Regex("""\d+(\.\d+)?""").find(rating)?.value?.toDoubleOrNull()
}

fun appendQueryTerm(source: String, value: String): String {
    val normalizedSource = source.trim()
    return when {
        normalizedSource.isBlank() -> value
        normalizedSource.contains(value) -> normalizedSource
        else -> "$normalizedSource，$value"
    }
}

fun cookSummary(query: String): String {
    return if (query.contains("两荤") || query.contains("一汤") || query.contains("主食")) {
        "你需要一套包含荤菜、素菜、汤和主食的晚餐组合，口味偏微辣。"
    } else if (query.contains("清淡")) {
        "你想要清淡少油、结构完整、操作简单的在家晚餐。"
    } else {
        "你想吃微辣、少油、适合在家完成的晚餐。"
    }
}

fun cookTags(query: String): List<String> {
    val tags = mutableListOf("组合菜单", "晚餐")
    if (query.contains("两荤")) tags.add("两荤一素")
    if (query.contains("一汤")) tags.add("一汤")
    if (query.contains("主食")) tags.add("主食")
    if (query.contains("微辣")) tags.add("微辣")
    if (query.contains("清淡")) return listOf("清淡", "少油", "快手", "晚餐")
    return tags
}
