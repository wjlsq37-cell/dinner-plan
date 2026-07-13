package com.dinnerplan.chidian

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
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
import kotlin.random.Random

private val Context.chiDianDataStore by preferencesDataStore(name = "chidian_settings")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChiDianApp()
        }
    }
}

private val PersistenceJson = Json {
    ignoreUnknownKeys = true
}

private const val DEFAULT_BACKEND_BASE_URL = "https://dinner-plan.vercel.app"
private const val FIXED_RESTAURANT_RESULT_LIMIT = 50
private const val DECISION_COOK_QUERY = ""
private const val DECISION_RESTAURANT_QUERY = ""
private const val HOME_TODAY_INSPIRATION_ITEM_INDEX = 3

private object SettingsKeys {
    val BackendBaseUrl = stringPreferencesKey("backend_base_url")
    val DeveloperEnabled = stringPreferencesKey("developer_enabled")
    val DeveloperAiProvider = stringPreferencesKey("developer_ai_provider")
    val DeveloperAiBaseUrl = stringPreferencesKey("developer_ai_base_url")
    val DeveloperAiApiKey = stringPreferencesKey("developer_ai_api_key")
    val DeveloperAiModel = stringPreferencesKey("developer_ai_model")
    val DeveloperAmapWebKey = stringPreferencesKey("developer_amap_web_key")
    val DeveloperRecipeApiSource = stringPreferencesKey("developer_recipe_api_source")
    val DeveloperRecipeApiBaseUrl = stringPreferencesKey("developer_recipe_api_base_url")
    val DeveloperRecipeApiAppId = stringPreferencesKey("developer_recipe_api_app_id")
    val DeveloperRecipeApiSecret = stringPreferencesKey("developer_recipe_api_secret")
    val DeveloperWanweiRecipeAppKey = stringPreferencesKey("developer_wanwei_recipe_app_key")
    val DeveloperWanweiRecipePageSize = stringPreferencesKey("developer_wanwei_recipe_page_size")
    val DeveloperMaxWaitSeconds = stringPreferencesKey("developer_max_wait_seconds")
    val CurrentLatitude = stringPreferencesKey("current_latitude")
    val CurrentLongitude = stringPreferencesKey("current_longitude")
    val LocationSource = stringPreferencesKey("location_source")
    val SavedMealIds = stringPreferencesKey("saved_meal_ids")
    val SavedRecipeIds = stringPreferencesKey("saved_recipe_ids")
    val SavedRestaurantIds = stringPreferencesKey("saved_restaurant_ids")
    val SavedOrder = stringPreferencesKey("saved_order")
    val SavedMealPlanCache = stringPreferencesKey("saved_meal_plan_cache")
    val SavedRecipeCache = stringPreferencesKey("saved_recipe_cache")
    val SavedRestaurantCache = stringPreferencesKey("saved_restaurant_cache")
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
    val LastCookQuery = stringPreferencesKey("last_cook_query")
    val LastCookSourceMode = stringPreferencesKey("last_cook_source_mode")
    val LastCookRecommendMode = stringPreferencesKey("last_cook_recommend_mode")
    val LastCookRecipes = stringPreferencesKey("last_cook_recipes")
    val LastCookMealPlans = stringPreferencesKey("last_cook_meal_plans")
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

sealed interface DecisionTarget {
    data class MealPlan(val id: String) : DecisionTarget
    data class Recipe(val id: String) : DecisionTarget
    data class Restaurant(val id: String) : DecisionTarget
}

fun decisionGreetingForHour(hour: Int): String {
    return when (hour.coerceIn(0, 23)) {
        in 5..10 -> "早上先吃点舒服的"
        in 11..14 -> "午饭交给我来拍板"
        in 17..21 -> "晚饭别再纠结了"
        else -> "这会儿吃点不费劲的"
    }
}

fun decisionSubcopyForHour(hour: Int): String {
    return when (hour.coerceIn(0, 23)) {
        in 5..10 -> "从清爽、热乎、方便里直接选一个今天的方向。"
        in 11..14 -> "做一道顺手菜，或者去附近找一家合适的店。"
        in 17..21 -> "不用来回翻列表，我先帮你挑一个能落地的选择。"
        else -> "少一点选择压力，先给你一个可以马上看的答案。"
    }
}

fun chooseDecisionTarget(
    mealPlanIds: List<String>,
    recipeIds: List<String>,
    restaurantIds: List<String>,
    random: Random = Random.Default
): DecisionTarget? {
    val candidates = mealPlanIds.filter { it.isNotBlank() }.distinct().map { DecisionTarget.MealPlan(it) } +
        recipeIds.filter { it.isNotBlank() }.distinct().map { DecisionTarget.Recipe(it) } +
        restaurantIds.filter { it.isNotBlank() }.distinct().map { DecisionTarget.Restaurant(it) }
    if (candidates.isEmpty()) return null
    return candidates[random.nextInt(candidates.size)]
}

fun chooseDecisionTarget(
    recipeIds: List<String>,
    restaurantIds: List<String>,
    random: Random = Random.Default
): DecisionTarget? {
    return chooseDecisionTarget(emptyList(), recipeIds, restaurantIds, random)
}

fun chooseDecisionRecipeId(recipeIds: List<String>, random: Random = Random.Default): String? {
    val candidates = recipeIds.filter { it.isNotBlank() }.distinct()
    if (candidates.isEmpty()) return null
    return candidates[random.nextInt(candidates.size)]
}

internal fun apiDecisionRecipes(recipes: List<Recipe>): List<Recipe> {
    return recipes
        .filter { !it.source.isNullOrBlank() && !it.source.equals("seed", ignoreCase = true) }
        .distinctBy { it.id }
}

fun chooseDecisionRestaurantId(restaurantIds: List<String>, random: Random = Random.Default): String? {
    val candidates = restaurantIds.filter { it.isNotBlank() }.distinct()
    if (candidates.isEmpty()) return null
    return candidates[random.nextInt(candidates.size)]
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
    val source: String? = null,
    val stepImageUrls: List<String> = emptyList()
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
    val preferOpenRestaurants: Boolean = true,
    val restaurantResultLimit: Int = FIXED_RESTAURANT_RESULT_LIMIT
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
    val cookQuery: String = "",
    val restaurantQuery: String = "",
    val backendBaseUrl: String = DEFAULT_BACKEND_BASE_URL,
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
    val savedOrder: List<SavedItem> = listOf(
        SavedItem.Meal("meal_spicy_combo"),
        SavedItem.RecipeItem("recipe_beef_tomato"),
        SavedItem.RestaurantItem("restaurant_noodle")
    ),
    val savedMealPlanCache: List<MealPlan> = emptyList(),
    val savedRecipeCache: List<Recipe> = emptyList(),
    val savedRestaurantCache: List<Restaurant> = emptyList(),
    val history: List<SavedItem> = listOf(
        SavedItem.Meal("meal_spicy_combo"),
        SavedItem.RecipeItem("recipe_chicken_pepper"),
        SavedItem.RestaurantItem("restaurant_hunan")
    ),
    val preferences: UserPreference = UserPreference(),
    val mealPlans: List<MealPlan> = emptyList(),
    val recipes: List<Recipe> = emptyList(),
    internal val hasCookSearchResult: Boolean = false,
    val recipeCache: List<Recipe> = emptyList(),
    val restaurants: List<Restaurant> = emptyList(),
    val restaurantCache: List<Restaurant> = emptyList(),
    val decisionRecipe: Recipe? = null,
    val decisionRestaurant: Restaurant? = null,
    val isDecisionLoading: Boolean = false,
    val decisionGenerationId: Long = 0,
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
        requestId: String? = null,
        broadSearch: Boolean = false
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
        preferences: UserPreference,
        broadSearch: Boolean = false
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

private data class DecisionCandidatePool(
    val mealPlans: List<MealPlan> = emptyList(),
    val recipes: List<Recipe> = emptyList(),
    val restaurants: List<Restaurant> = emptyList(),
    val restaurantLocationUsed: String = ""
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
        requestId: String?,
        broadSearch: Boolean
    ): CookResult {
        return try {
            val response = backendApiClient.recommendCook(
                baseUrl = backendBaseUrl,
                request = RecommendationRequest(
                    query = query,
                    mode = mode.toDto(),
                    preferences = preferences.toDto(),
                    cookSource = cookSourceMode.toDto(),
                    requestId = requestId,
                    broadSearch = broadSearch
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
            logInternalIssue("Backend cook recommendation failed", error.message, error)
            CookResult(
                mealPlans = localMealPlans(query),
                recipes = localRecipes(query),
                fallbackReason = friendlyStatusMessage(error.message, UserMessageContext.Recipe)
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
            logInternalIssue("Backend cook cancellation failed", error.message, error)
            CancelCookResult(
                cancelled = false,
                message = "已取消本次菜谱搜索。"
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
        preferences: UserPreference,
        broadSearch: Boolean
    ): RestaurantResult {
        return runCatching {
            val response = backendApiClient.recommendRestaurants(
                baseUrl = backendBaseUrl,
                request = RecommendationRequest(
                    query = query,
                    mode = RecommendationModeDto.RESTAURANT,
                    location = location,
                    preferences = preferences.toDto(),
                    broadSearch = broadSearch
                )
            )
            RestaurantResult(
                restaurants = response.restaurants.map { it.toUi() },
                locationUsed = response.locationUsed?.text.orEmpty().ifBlank { locationText },
                fallbackReason = response.fallbackReason
            )
        }.getOrElse { error ->
            logInternalIssue("Backend restaurant recommendation failed", error.message, error)
            RestaurantResult(
                restaurants = emptyList(),
                locationUsed = locationText,
                fallbackReason = friendlyStatusMessage(error.message, UserMessageContext.Restaurant)
            )
        }
    }
}

private class DirectCookRecommendationRepository(
    private val directAiApiClient: DirectAiApiClient,
    private val recipeApiClient: WanweiRecipeApiClient
) : AiRecommendationRepository {
    override suspend fun recommendCook(
        backendBaseUrl: String,
        developerSettings: DeveloperSettings,
        query: String,
        mode: RecommendMode,
        cookSourceMode: CookSourceMode,
        preferences: UserPreference,
        requestId: String?,
        broadSearch: Boolean
    ): CookResult {
        return when (cookSourceMode) {
            CookSourceMode.Database -> recommendFromRecipeApi(developerSettings, query, mode, broadSearch)
            CookSourceMode.AiGenerated -> {
                if (developerSettings.aiBaseUrl.isBlank() || developerSettings.aiApiKey.isBlank() || developerSettings.aiModel.isBlank()) {
                    logInternalIssue("Direct AI cook configuration missing", "aiBaseUrl/apiKey/model is blank")
                    return recommendFromRecipeApi(developerSettings, query, mode, broadSearch)
                        .withFallbackPrefix(friendlyStatusMessage("配置缺失", UserMessageContext.Config))
                }
                val aiResponse = runCatching {
                    directAiApiClient.generateCookRecommendation(
                        settings = developerSettings,
                        query = query,
                        mode = mode.toDto(),
                        preferences = preferences.toDto()
                    )
                }.getOrElse { error ->
                    logInternalIssue("Direct AI cook recommendation failed", error.message, error)
                    return recommendFromRecipeApi(developerSettings, query, mode, broadSearch)
                        .withFallbackPrefix(friendlyStatusMessage(error.message, UserMessageContext.Ai))
                }
                if (aiResponse != null) {
                    CookResult(
                        mealPlans = aiResponse.mealPlans.map { it.toUi() },
                        recipes = aiResponse.recipes.map { it.toUi() },
                        fallbackReason = aiResponse.fallbackReason
                    )
                } else {
                    recommendFromRecipeApi(developerSettings, query, mode, broadSearch)
                        .withFallbackPrefix(friendlyStatusMessage("AI 请求失败", UserMessageContext.Ai))
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

    private suspend fun recommendFromRecipeApi(
        developerSettings: DeveloperSettings,
        query: String,
        mode: RecommendMode,
        broadSearch: Boolean
    ): CookResult {
        return if (mode == RecommendMode.SingleRecipe) {
            val searchTerms = if (broadSearch) broadRecipeSearchTerms() else listOf(query)
            val results = searchTerms.map { searchQuery ->
                recipeApiClient.searchRecipes(developerSettings, searchQuery)
            }
            val recipes = results
                .flatMap { it.recipes }
                .distinctBy { it.id }
                .take(developerSettings.safePageSize)
            CookResult(
                mealPlans = emptyList(),
                recipes = recipes.map { it.toUi() },
                fallbackReason = results.firstOrNull { it.fallbackReason != null }?.fallbackReason.takeIf { recipes.isEmpty() }
            )
        } else {
            val slotQueries = if (broadSearch) broadRecipeSearchTerms() else wanweiMealSlotQueries(query)
            val slotResults = slotQueries.map { slotQuery ->
                recipeApiClient.searchRecipes(developerSettings, slotQuery)
            }
            val recipes = slotResults
                .flatMap { it.recipes }
                .distinctBy { it.id }
                .map { it.toUi() }
                .take(developerSettings.safePageSize)
            val fallbackReason = slotResults.firstOrNull { it.fallbackReason != null }?.fallbackReason
            CookResult(
                mealPlans = buildWanweiMealPlan(query, recipes, developerSettings.recipeSourceLabel, broadSearch)?.let(::listOf).orEmpty(),
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
        preferences: UserPreference,
        broadSearch: Boolean
    ): RestaurantResult {
        return runCatching {
            val response = directAmapApiClient.searchRestaurants(
                settings = developerSettings,
                query = query,
                locationText = locationText,
                location = location,
                preferences = preferences.toDto(),
                broadSearch = broadSearch
            )
            RestaurantResult(
                restaurants = response.restaurants.map { it.toUi() },
                locationUsed = response.locationUsed?.text.orEmpty().ifBlank { locationText },
                fallbackReason = response.fallbackReason
            )
        }.getOrElse { error ->
            logInternalIssue("Direct Amap restaurant recommendation failed", error.message, error)
            RestaurantResult(
                restaurants = emptyList(),
                locationUsed = locationText,
                fallbackReason = friendlyStatusMessage(error.message, UserMessageContext.Restaurant)
            )
        }
    }
}

private suspend fun loadDecisionCandidates(
    snapshot: AppUiState,
    cookRepository: AiRecommendationRepository,
    restaurantRepository: RestaurantRepository
): DecisionCandidatePool {
    val mealResult = fetchDecisionCookResult(
        repository = cookRepository,
        snapshot = snapshot,
        mode = RecommendMode.MealPlan
    )
    val recipeResult = fetchDecisionCookResult(
        repository = cookRepository,
        snapshot = snapshot,
        mode = RecommendMode.SingleRecipe
    )
    val restaurantResult = fetchDecisionRestaurantResult(
        repository = restaurantRepository,
        snapshot = snapshot
    )
    return DecisionCandidatePool(
        mealPlans = mealResult.mealPlans.distinctBy { it.id },
        recipes = (recipeResult.recipes + mealResult.recipes).distinctBy { it.id },
        restaurants = restaurantResult.restaurants.distinctBy { it.id },
        restaurantLocationUsed = restaurantResult.locationUsed
    )
}

private suspend fun fetchDecisionCookResult(
    repository: AiRecommendationRepository,
    snapshot: AppUiState,
    mode: RecommendMode
): CookResult {
    return try {
        repository.recommendCook(
            backendBaseUrl = snapshot.backendBaseUrl,
            developerSettings = snapshot.developerSettings,
            query = DECISION_COOK_QUERY,
            mode = mode,
            cookSourceMode = CookSourceMode.Database,
            preferences = snapshot.preferences,
            requestId = null,
            broadSearch = true
        )
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        logInternalIssue("Decision cook candidate loading failed", error.message, error)
        CookResult(emptyList(), emptyList(), null)
    }
}

private suspend fun fetchDecisionRestaurantResult(
    repository: RestaurantRepository,
    snapshot: AppUiState
): RestaurantResult {
    val latitude = snapshot.currentLatitude
    val longitude = snapshot.currentLongitude
    if (latitude != null && longitude != null && !isLikelyAmapSearchCoordinate(latitude, longitude)) {
        logInternalIssue("Decision restaurant candidate skipped", unsupportedAmapLocationMessage(latitude, longitude))
        return RestaurantResult(emptyList(), snapshot.locationText, null)
    }
    return try {
        repository.nearbyRestaurants(
            backendBaseUrl = snapshot.backendBaseUrl,
            developerSettings = snapshot.developerSettings,
            query = DECISION_RESTAURANT_QUERY,
            locationText = snapshot.locationText,
            location = restaurantLocationForSearch(
                locationText = snapshot.locationText,
                latitude = snapshot.currentLatitude,
                longitude = snapshot.currentLongitude
            ),
            preferences = snapshot.preferences,
            broadSearch = true
        )
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        logInternalIssue("Decision restaurant candidate loading failed", error.message, error)
        RestaurantResult(emptyList(), snapshot.locationText, null)
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

private fun broadRecipeSearchTerms(): List<String> {
    return listOf("家常菜", "鸡蛋", "豆腐", "鸡肉", "猪肉", "牛肉", "鱼", "青菜", "土豆", "茄子", "汤", "面", "饭")
}

private fun buildWanweiMealPlan(
    query: String,
    recipes: List<Recipe>,
    sourceLabel: String = "菜谱 API",
    broadSearch: Boolean = false
): MealPlan? {
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
        title = if (broadSearch) "随机推荐组合菜单" else "${query.take(12).ifBlank { "家常" }}组合菜单",
        structure = dishes.joinToString(" · ") { it.course },
        cookTime = "约${(selected.size * 18).coerceAtLeast(35)}分钟",
        servings = "2-3 人份",
        coverUrl = selected.firstOrNull { it.coverUrl.isNotBlank() }?.coverUrl.orEmpty(),
        tags = ((if (broadSearch) listOf("随机推荐", "组合菜单") else cookTags(query)) + sourceLabel).distinct().take(8),
        reason = if (broadSearch) {
            "从${sourceLabel}菜谱库扩大候选范围随机组装，优先避开你的忌口。"
        } else {
            "按你的需求从${sourceLabel}菜谱库拆分检索并组装，结果优先保留星级更高、步骤更完整的菜谱。"
        },
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
        preferOpenRestaurants = preferOpenRestaurants,
        restaurantResultLimit = FIXED_RESTAURANT_RESULT_LIMIT
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

private fun MealPlan.toDto(): MealPlanDto {
    return MealPlanDto(
        id = id,
        title = title,
        structure = structure,
        cookTime = cookTime,
        servings = servings,
        coverUrl = coverUrl,
        tags = tags,
        reason = reason,
        dishes = dishes.map { it.toDto() },
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

private fun DishItem.toDto(): DishItemDto {
    return DishItemDto(
        course = course,
        name = name,
        note = note,
        badge = when (badge) {
            DishBadge.Meat -> "Meat"
            DishBadge.Veg -> "Veg"
            DishBadge.Soup -> "Soup"
            DishBadge.Staple -> "Staple"
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
        source = source,
        stepImageUrls = stepImageUrls
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
        source = source,
        stepImageUrls = stepImageUrls
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
    val lastCookRecipes = decodeRecipeCache(preferences[SettingsKeys.LastCookRecipes])
    val lastCookMealPlans = decodeMealPlanCache(preferences[SettingsKeys.LastCookMealPlans])
    val hasLastCookResult = lastCookRecipes.isNotEmpty() || lastCookMealPlans.isNotEmpty()
    val savedMealIds = preferences[SettingsKeys.SavedMealIds]?.let(::decodeSet) ?: current.savedMealIds
    val savedRecipeIds = preferences[SettingsKeys.SavedRecipeIds]?.let(::decodeSet) ?: current.savedRecipeIds
    val savedRestaurantIds = preferences[SettingsKeys.SavedRestaurantIds]?.let(::decodeSet) ?: current.savedRestaurantIds
    val savedMealPlanCache = (
        decodeMealPlanCache(preferences[SettingsKeys.SavedMealPlanCache]) +
            current.savedMealPlanCache +
            current.mealPlans.filter { it.id in savedMealIds }
        ).distinctBy { it.id }
    val savedRecipeCache = (
        decodeRecipeCache(preferences[SettingsKeys.SavedRecipeCache]) +
            cachedRecipes.filter { it.id in savedRecipeIds } +
            current.savedRecipeCache +
            current.recipes.filter { it.id in savedRecipeIds }
        ).distinctBy { it.id }
    val savedRestaurantCache = (
        decodeRestaurantCache(preferences[SettingsKeys.SavedRestaurantCache]) +
            cachedRestaurants.filter { it.id in savedRestaurantIds } +
            current.savedRestaurantCache +
            current.restaurants.filter { it.id in savedRestaurantIds }
        ).distinctBy { it.id }
    val savedOrder = savedOrderFrom(
        persisted = preferences[SettingsKeys.SavedOrder]?.let(::decodeHistory),
        current = current.savedOrder,
        mealIds = savedMealIds,
        recipeIds = savedRecipeIds,
        restaurantIds = savedRestaurantIds
    )
    return current.copy(
        cookQuery = preferences[SettingsKeys.LastCookQuery]?.takeIf { hasLastCookResult } ?: current.cookQuery,
        cookSourceMode = preferences[SettingsKeys.LastCookSourceMode]
            ?.takeIf { hasLastCookResult }
            ?.let(::decodeCookSourceMode)
            ?: current.cookSourceMode,
        recommendMode = preferences[SettingsKeys.LastCookRecommendMode]
            ?.takeIf { hasLastCookResult }
            ?.let(::decodeRecommendMode)
            ?: current.recommendMode,
        backendBaseUrl = preferences[SettingsKeys.BackendBaseUrl] ?: current.backendBaseUrl,
        developerSettings = current.developerSettings.copy(
            enabled = preferences[SettingsKeys.DeveloperEnabled]?.toBooleanStrictOrNull()
                ?: current.developerSettings.enabled,
            aiProvider = preferences[SettingsKeys.DeveloperAiProvider] ?: current.developerSettings.aiProvider,
            aiBaseUrl = preferences[SettingsKeys.DeveloperAiBaseUrl] ?: current.developerSettings.aiBaseUrl,
            aiApiKey = preferences[SettingsKeys.DeveloperAiApiKey] ?: current.developerSettings.aiApiKey,
            aiModel = preferences[SettingsKeys.DeveloperAiModel] ?: current.developerSettings.aiModel,
            amapWebKey = preferences[SettingsKeys.DeveloperAmapWebKey] ?: current.developerSettings.amapWebKey,
            recipeApiSource = preferences[SettingsKeys.DeveloperRecipeApiSource]
                ?: current.developerSettings.recipeApiSource,
            recipeApiBaseUrl = preferences[SettingsKeys.DeveloperRecipeApiBaseUrl]
                ?: current.developerSettings.recipeApiBaseUrl,
            recipeApiAppId = preferences[SettingsKeys.DeveloperRecipeApiAppId]
                ?: current.developerSettings.recipeApiAppId,
            recipeApiSecret = preferences[SettingsKeys.DeveloperRecipeApiSecret]
                ?: current.developerSettings.recipeApiSecret,
            wanweiRecipeAppKey = preferences[SettingsKeys.DeveloperWanweiRecipeAppKey]
                ?: current.developerSettings.wanweiRecipeAppKey,
            wanweiRecipePageSize = preferences[SettingsKeys.DeveloperWanweiRecipePageSize]?.toIntOrNull()
                ?.coerceIn(1, 50)
                ?: current.developerSettings.wanweiRecipePageSize,
            maxWaitSeconds = preferences[SettingsKeys.DeveloperMaxWaitSeconds]?.toIntOrNull()
                ?.coerceIn(10, 300)
                ?: current.developerSettings.maxWaitSeconds
        ),
        savedMealIds = savedMealIds,
        savedRecipeIds = savedRecipeIds,
        savedRestaurantIds = savedRestaurantIds,
        savedOrder = savedOrder,
        savedMealPlanCache = savedMealPlanCache,
        savedRecipeCache = savedRecipeCache,
        savedRestaurantCache = savedRestaurantCache,
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
                ?: current.preferences.preferOpenRestaurants,
            restaurantResultLimit = FIXED_RESTAURANT_RESULT_LIMIT
        ),
        locationText = preferences[SettingsKeys.LocationText] ?: current.locationText,
        currentLatitude = preferences[SettingsKeys.CurrentLatitude]?.toDoubleOrNull(),
        currentLongitude = preferences[SettingsKeys.CurrentLongitude]?.toDoubleOrNull(),
        locationSource = preferences[SettingsKeys.LocationSource] ?: current.locationSource,
        mealPlans = lastCookMealPlans.ifEmpty { current.mealPlans },
        recipes = lastCookRecipes.ifEmpty { current.recipes },
        hasCookSearchResult = hasLastCookResult,
        recipeCache = (savedRecipeCache + lastCookRecipes + cachedRecipes + current.recipeCache + current.recipes).distinctBy { it.id },
        restaurantCache = (savedRestaurantCache + current.restaurantCache).distinctBy { it.id }
    )
}

private suspend fun persistUiState(context: Context, state: AppUiState) {
    context.chiDianDataStore.edit { preferences ->
        preferences[SettingsKeys.BackendBaseUrl] = state.backendBaseUrl
        preferences[SettingsKeys.DeveloperEnabled] = state.developerSettings.enabled.toString()
        preferences[SettingsKeys.DeveloperAiProvider] = state.developerSettings.selectedAiProvider.id
        preferences[SettingsKeys.DeveloperAiBaseUrl] = state.developerSettings.aiBaseUrl
        preferences[SettingsKeys.DeveloperAiApiKey] = state.developerSettings.aiApiKey
        preferences[SettingsKeys.DeveloperAiModel] = state.developerSettings.aiModel
        preferences[SettingsKeys.DeveloperAmapWebKey] = state.developerSettings.amapWebKey
        preferences[SettingsKeys.DeveloperRecipeApiSource] = state.developerSettings.selectedRecipeApiSource.id
        preferences[SettingsKeys.DeveloperRecipeApiBaseUrl] = state.developerSettings.recipeApiBaseUrl
        preferences[SettingsKeys.DeveloperRecipeApiAppId] = state.developerSettings.recipeApiAppId
        preferences[SettingsKeys.DeveloperRecipeApiSecret] = state.developerSettings.recipeApiSecret
        preferences[SettingsKeys.DeveloperWanweiRecipeAppKey] = state.developerSettings.wanweiRecipeAppKey
        preferences[SettingsKeys.DeveloperWanweiRecipePageSize] = state.developerSettings.safePageSize.toString()
        preferences[SettingsKeys.DeveloperMaxWaitSeconds] = state.developerSettings.safeMaxWaitSeconds.toString()
        preferences[SettingsKeys.SavedMealIds] = encodeList(state.savedMealIds.toList())
        preferences[SettingsKeys.SavedRecipeIds] = encodeList(state.savedRecipeIds.toList())
        preferences[SettingsKeys.SavedRestaurantIds] = encodeList(state.savedRestaurantIds.toList())
        preferences[SettingsKeys.SavedOrder] = encodeHistory(state.savedOrder.filterSavedItems(state.savedMealIds, state.savedRecipeIds, state.savedRestaurantIds))
        preferences[SettingsKeys.SavedMealPlanCache] = encodeMealPlanCache(state.savedMealPlanCache.filter { it.id in state.savedMealIds })
        preferences[SettingsKeys.SavedRecipeCache] = encodeRecipeCache(state.savedRecipeCache.filter { it.id in state.savedRecipeIds })
        preferences[SettingsKeys.SavedRestaurantCache] = encodeRestaurantCache(state.savedRestaurantCache.filter { it.id in state.savedRestaurantIds })
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
        if (state.hasCookSearchResult && (state.recipes.isNotEmpty() || state.mealPlans.isNotEmpty())) {
            preferences[SettingsKeys.LastCookQuery] = state.cookQuery
            preferences[SettingsKeys.LastCookSourceMode] = state.cookSourceMode.name
            preferences[SettingsKeys.LastCookRecommendMode] = state.recommendMode.name
            preferences[SettingsKeys.LastCookRecipes] = encodeRecipeCache(state.recipes)
            preferences[SettingsKeys.LastCookMealPlans] = encodeMealPlanCache(state.mealPlans)
        } else {
            preferences.remove(SettingsKeys.LastCookQuery)
            preferences.remove(SettingsKeys.LastCookSourceMode)
            preferences.remove(SettingsKeys.LastCookRecommendMode)
            preferences.remove(SettingsKeys.LastCookRecipes)
            preferences.remove(SettingsKeys.LastCookMealPlans)
        }
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

private fun savedOrderFrom(
    persisted: List<SavedItem>?,
    current: List<SavedItem>,
    mealIds: Set<String>,
    recipeIds: Set<String>,
    restaurantIds: Set<String>
): List<SavedItem> {
    val ordered = (persisted ?: current).filterSavedItems(mealIds, recipeIds, restaurantIds)
    val missing = buildList {
        mealIds.forEach { id -> add(SavedItem.Meal(id)) }
        recipeIds.forEach { id -> add(SavedItem.RecipeItem(id)) }
        restaurantIds.forEach { id -> add(SavedItem.RestaurantItem(id)) }
    }.filterNot { candidate -> ordered.any { it.id == candidate.id && it::class == candidate::class } }
    return ordered + missing
}

private fun List<SavedItem>.filterSavedItems(
    mealIds: Set<String>,
    recipeIds: Set<String>,
    restaurantIds: Set<String>
): List<SavedItem> {
    return filter { item ->
        when (item) {
            is SavedItem.Meal -> item.id in mealIds
            is SavedItem.RecipeItem -> item.id in recipeIds
            is SavedItem.RestaurantItem -> item.id in restaurantIds
        }
    }.distinctBy { "${it::class.simpleName}:${it.id}" }
}

private fun decodeCookSourceMode(value: String): CookSourceMode {
    return when (value) {
        CookSourceMode.AiGenerated.name -> CookSourceMode.AiGenerated
        else -> CookSourceMode.Database
    }
}

private fun decodeRecommendMode(value: String): RecommendMode {
    return when (value) {
        RecommendMode.SingleRecipe.name -> RecommendMode.SingleRecipe
        else -> RecommendMode.MealPlan
    }
}

private fun decodeMealPlanCache(value: String?): List<MealPlan> {
    if (value.isNullOrBlank()) return emptyList()
    return runCatching {
        PersistenceJson.decodeFromString(ListSerializer(MealPlanDto.serializer()), value)
            .map { it.toUi() }
    }.getOrDefault(emptyList())
}

private fun encodeMealPlanCache(mealPlans: List<MealPlan>): String {
    return PersistenceJson.encodeToString(
        ListSerializer(MealPlanDto.serializer()),
        mealPlans.distinctBy { it.id }.map { it.toDto() }
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
    return (state.savedMealPlanCache + state.mealPlans + MockData.mealPlans)
        .distinctBy { it.id }
        .firstOrNull { it.id == id }
}

private fun findRecipe(id: String, state: AppUiState): Recipe? {
    return (listOfNotNull(state.decisionRecipe) + state.savedRecipeCache + state.recipes + state.recipeCache + MockData.recipes)
        .distinctBy { it.id }
        .firstOrNull { it.id == id }
}

private fun Recipe.needsLazyMxnzpDetail(): Boolean {
    val isMxnzp = source.equals("mxnzp", ignoreCase = true) || id.startsWith("mxnzp_")
    if (!isMxnzp) return false
    if (stepImageUrls.any { it.isNotBlank() }) return false
    return steps.size <= 1
}

private fun AppUiState.withUpdatedRecipeDetail(recipe: Recipe): AppUiState {
    return copy(
        recipes = recipes.replaceRecipe(recipe),
        recipeCache = recipeCache.upsertRecipe(recipe).take(80),
        savedRecipeCache = if (recipe.id in savedRecipeIds) {
            savedRecipeCache.upsertRecipe(recipe)
        } else {
            savedRecipeCache
        }
    )
}

private fun List<Recipe>.replaceRecipe(recipe: Recipe): List<Recipe> {
    return if (any { it.id == recipe.id }) {
        map { if (it.id == recipe.id) recipe else it }
    } else {
        this
    }
}

private fun List<Recipe>.upsertRecipe(recipe: Recipe): List<Recipe> {
    return if (any { it.id == recipe.id }) {
        map { if (it.id == recipe.id) recipe else it }
    } else {
        listOf(recipe) + this
    }
}

private fun findRestaurant(id: String, state: AppUiState): Restaurant? {
    return (listOfNotNull(state.decisionRestaurant) + state.savedRestaurantCache + state.restaurants + state.restaurantCache + MockData.restaurants)
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
    val directAiApiClient = remember { DirectAiApiClient() }
    val recipeApiClient = remember { WanweiRecipeApiClient() }
    val directCookRepository = remember {
        DirectCookRecommendationRepository(
            directAiApiClient = directAiApiClient,
            recipeApiClient = recipeApiClient
        )
    }
    val backendRestaurantRepository = remember { BackendRestaurantRepository(backendApiClient) }
    val directRestaurantRepository = remember {
        DirectRestaurantRepository(DirectAmapApiClient(directAiApiClient = directAiApiClient))
    }
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
    val homeListState = rememberLazyListState()
    val cookListState = rememberLazyListState()
    val nearbyListState = rememberLazyListState()
    val savedListState = rememberLazyListState()
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
        uiState.savedOrder,
        uiState.savedMealPlanCache,
        uiState.savedRecipeCache,
        uiState.savedRestaurantCache,
        uiState.history,
        uiState.preferences,
        uiState.tasteOptions,
        uiState.avoidOptions,
        uiState.locationText,
        uiState.currentLatitude,
        uiState.currentLongitude,
        uiState.locationSource,
        uiState.cookQuery,
        uiState.cookSourceMode,
        uiState.recommendMode,
        uiState.mealPlans,
        uiState.recipes,
        uiState.hasCookSearchResult,
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

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun addHistory(item: SavedItem) {
        uiState = uiState.copy(history = (listOf(item) + uiState.history.filterNot { it == item }).take(10))
    }

    fun toggleMeal(id: String) {
        val isRemoving = id in uiState.savedMealIds
        val mealPlan = findMealPlan(id, uiState)
        if (!isRemoving && mealPlan == null) {
            toast("保存失败：未找到这套菜单")
            return
        }
        val next = if (isRemoving) uiState.savedMealIds - id else uiState.savedMealIds + id
        val savedCache = if (!isRemoving && mealPlan != null) {
            (listOf(mealPlan) + uiState.savedMealPlanCache).distinctBy { it.id }
        } else {
            uiState.savedMealPlanCache.filterNot { it.id == id }
        }
        val savedOrder = if (isRemoving) {
            uiState.savedOrder.filterNot { it is SavedItem.Meal && it.id == id }
        } else {
            listOf(SavedItem.Meal(id)) + uiState.savedOrder.filterNot { it is SavedItem.Meal && it.id == id }
        }
        uiState = uiState.copy(savedMealIds = next, savedMealPlanCache = savedCache, savedOrder = savedOrder)
        toast(if (id in next) "整套菜单已收藏" else "已取消收藏整套菜单")
    }

    fun toggleRecipe(id: String) {
        val isRemoving = id in uiState.savedRecipeIds
        val recipe = findRecipe(id, uiState)
        if (!isRemoving && recipe == null) {
            toast("保存失败：未找到这道菜谱")
            return
        }
        val next = if (isRemoving) uiState.savedRecipeIds - id else uiState.savedRecipeIds + id
        val savedCache = if (!isRemoving && recipe != null) {
            (listOf(recipe) + uiState.savedRecipeCache).distinctBy { it.id }
        } else {
            uiState.savedRecipeCache.filterNot { it.id == id }
        }
        val cache = if (recipe == null) uiState.recipeCache else (listOf(recipe) + uiState.recipeCache).distinctBy { it.id }
        val savedOrder = if (isRemoving) {
            uiState.savedOrder.filterNot { it is SavedItem.RecipeItem && it.id == id }
        } else {
            listOf(SavedItem.RecipeItem(id)) + uiState.savedOrder.filterNot { it is SavedItem.RecipeItem && it.id == id }
        }
        uiState = uiState.copy(savedRecipeIds = next, savedRecipeCache = savedCache, recipeCache = cache, savedOrder = savedOrder)
        toast(if (id in next) "菜谱已收藏" else "已取消收藏菜谱")
    }

    fun toggleRestaurant(id: String) {
        val isRemoving = id in uiState.savedRestaurantIds
        val restaurant = findRestaurant(id, uiState)
        if (!isRemoving && restaurant == null) {
            toast("保存失败：未找到这家餐厅")
            return
        }
        val next = if (isRemoving) uiState.savedRestaurantIds - id else uiState.savedRestaurantIds + id
        val savedCache = if (!isRemoving && restaurant != null) {
            (listOf(restaurant) + uiState.savedRestaurantCache).distinctBy { it.id }
        } else {
            uiState.savedRestaurantCache.filterNot { it.id == id }
        }
        val savedOrder = if (isRemoving) {
            uiState.savedOrder.filterNot { it is SavedItem.RestaurantItem && it.id == id }
        } else {
            listOf(SavedItem.RestaurantItem(id)) + uiState.savedOrder.filterNot { it is SavedItem.RestaurantItem && it.id == id }
        }
        uiState = uiState.copy(savedRestaurantIds = next, savedRestaurantCache = savedCache, savedOrder = savedOrder)
        toast(if (id in next) "餐厅已收藏" else "已取消收藏餐厅")
    }

    fun showDialog(title: String, message: String, action: String = "知道了") {
        uiState = uiState.copy(dialog = DialogState(title, message, action))
    }

    fun loadRecipeDetailIfNeeded(id: String) {
        val recipe = findRecipe(id, uiState) ?: return
        if (!recipe.needsLazyMxnzpDetail()) return
        scope.launch {
            val snapshot = uiState
            val latestRecipe = findRecipe(id, snapshot) ?: return@launch
            if (!latestRecipe.needsLazyMxnzpDetail()) return@launch
            val detailedRecipe = recipeApiClient.loadMxnzpRecipeDetail(
                settings = snapshot.developerSettings,
                recipe = latestRecipe.toDto()
            )?.toUi() ?: return@launch
            uiState = uiState.withUpdatedRecipeDetail(detailedRecipe)
        }
    }

    fun refreshCookRecommendations() {
        scope.launch { cookListState.animateScrollToItem(0) }
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
                    hasCookSearchResult = true,
                    recipeCache = (nextRecipes + snapshot.recipeCache).distinctBy { it.id }.take(80),
                    cookFallbackReason = result.fallbackReason
                )
                toast(
                    result.fallbackReason?.let { friendlyStatusMessage(it, UserMessageContext.Recipe) }
                        ?: if (snapshot.cookSourceMode == CookSourceMode.Database) "已找到做饭推荐" else "已生成做饭推荐"
                )
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
                    else -> friendlyStatusMessage(result.message, UserMessageContext.Ai)
                }
            )
        }
    }

    fun refreshRestaurants(queryOverride: String? = null, showToast: Boolean = true) {
        scope.launch {
            nearbyListState.animateScrollToItem(0)
            val snapshot = uiState
            val queryText = queryOverride ?: snapshot.restaurantQuery.ifBlank { "餐厅" }
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
                query = queryText,
                locationText = snapshot.locationText,
                location = location,
                preferences = snapshot.preferences
            )
            uiState = uiState.copy(
                isRestaurantLoading = false,
                restaurants = result.restaurants,
                restaurantFallbackReason = result.fallbackReason,
                lastRestaurantLocation = result.locationUsed
            )
            if (showToast) {
                toast(result.fallbackReason?.let { friendlyStatusMessage(it, UserMessageContext.Restaurant) } ?: "已找到附近餐厅")
            }
        }
    }

    fun autoRefreshNearbyRestaurantsIfEmpty() {
        if (!settingsLoaded || uiState.restaurants.isNotEmpty() || uiState.isRestaurantLoading) return
        val query = "餐厅"
        refreshRestaurants(queryOverride = query, showToast = false)
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
                        toast(friendlyStatusMessage(unsupportedAmapLocationMessage(result.latitude, result.longitude), UserMessageContext.Location))
                        return@launch
                    }
                    toast("已获取当前位置，正在搜索附近餐厅")
                    refreshRestaurants()
                }
                is DeviceLocationResult.Unavailable -> {
                    logInternalIssue("Device location unavailable", result.message)
                    showDialog("定位暂时不可用", friendlyStatusMessage(result.message, UserMessageContext.Location))
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

    fun applyManualRestaurantLocation(location: String) {
        val normalizedLocation = location.trim()
        if (normalizedLocation.isBlank()) {
            toast("请输入地址或商圈")
            return
        }
        uiState = uiState.copy(
            locationText = normalizedLocation,
            currentLatitude = null,
            currentLongitude = null,
            locationSource = "",
            lastRestaurantLocation = normalizedLocation
        )
        refreshRestaurants(showToast = true)
    }

    fun decideWhatToEat() {
        scope.launch {
            if (uiState.isDecisionLoading) {
                return@launch
            }
            toast("正在帮你决定")
            uiState = uiState.copy(isDecisionLoading = true)
            if (!isLazyItemMeaningfullyVisible(homeListState, HOME_TODAY_INSPIRATION_ITEM_INDEX)) {
                launch {
                    homeListState.animateScrollToItem(HOME_TODAY_INSPIRATION_ITEM_INDEX)
                }
            }
            var decisionCompleted = false
            try {
                val snapshot = uiState
                val cookRepository = if (snapshot.developerSettings.enabled) directCookRepository else backendAiRepository
                val restaurantRepository = if (snapshot.developerSettings.enabled) directRestaurantRepository else backendRestaurantRepository
                val candidates = loadDecisionCandidates(
                    snapshot = snapshot,
                    cookRepository = cookRepository,
                    restaurantRepository = restaurantRepository
                )
                val apiRecipes = apiDecisionRecipes(candidates.recipes)
                val nextState = uiState.copy(
                    mealPlans = candidates.mealPlans.ifEmpty { uiState.mealPlans },
                    recipes = apiRecipes.ifEmpty { uiState.recipes },
                    recipeCache = (apiRecipes + uiState.recipeCache).distinctBy { it.id }.take(80),
                    restaurants = candidates.restaurants.ifEmpty { uiState.restaurants },
                    restaurantCache = (candidates.restaurants + uiState.restaurantCache).distinctBy { it.id },
                    lastRestaurantLocation = candidates.restaurantLocationUsed.ifBlank { uiState.lastRestaurantLocation }
                )
                val recipePool = apiDecisionRecipes(candidates.recipes)
                val restaurantPool = candidates.restaurants.ifEmpty { nextState.restaurants }
                    .distinctBy { it.id }
                val selectedRecipeId = chooseDecisionRecipeId(recipePool.map { it.id })
                val selectedRestaurantId = chooseDecisionRestaurantId(restaurantPool.map { it.id })
                val selectedRecipe = recipePool.firstOrNull { it.id == selectedRecipeId }
                val selectedRestaurant = restaurantPool.firstOrNull { it.id == selectedRestaurantId }
                val hasDecisionResult = selectedRecipe != null || selectedRestaurant != null
                uiState = nextState.copy(
                    decisionRecipe = selectedRecipe,
                    decisionRestaurant = selectedRestaurant,
                    isDecisionLoading = false,
                    decisionGenerationId = if (hasDecisionResult) nextState.decisionGenerationId + 1 else nextState.decisionGenerationId
                )
                decisionCompleted = true
                toast(
                    when {
                        selectedRecipe != null && selectedRestaurant != null -> "已为你选好一菜一店"
                        selectedRecipe != null -> "已为你选好一道菜"
                        selectedRestaurant != null -> "已为你选好一家店"
                        else -> "暂时没找到合适结果，可以稍后再试"
                    }
                )
                if (selectedRecipe == null && selectedRestaurant == null) {
                    refreshRestaurants(showToast = false)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                logInternalIssue("Decision generation failed", error.message, error)
                toast(friendlyStatusMessage(error.message, UserMessageContext.Ai))
            } finally {
                if (!decisionCompleted) {
                    uiState = uiState.copy(isDecisionLoading = false)
                }
            }
        }
    }

    LaunchedEffect(settingsLoaded) {
        if (settingsLoaded) {
            autoRefreshNearbyRestaurantsIfEmpty()
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
            MapNavigationResult.Opened -> toast("已打开高德地图")
            MapNavigationResult.MissingDestination -> showDialog("无法导航", "这个餐厅缺少经纬度和地址，暂时无法打开地图。")
            is MapNavigationResult.Failed -> {
                logInternalIssue("Open Amap navigation failed", result.message)
                showDialog("无法打开高德地图", "未安装或无法打开高德地图。")
            }
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
                        listState = homeListState,
                        onCookSearch = {
                            uiState = uiState.copy(recommendMode = RecommendMode.MealPlan)
                            navigate(Screen.CookRecommend)
                            toast("进入自己做")
                        },
                        onRestaurantSearch = {
                            navigate(Screen.NearbyRestaurant)
                            toast("已按附近吃需求搜索餐厅")
                        },
                        onDecision = ::decideWhatToEat,
                        decisionRecipe = uiState.decisionRecipe,
                        decisionRestaurant = uiState.decisionRestaurant,
                        isDecisionLoading = uiState.isDecisionLoading,
                        decisionGenerationId = uiState.decisionGenerationId,
                        onSettings = { topLevel(Screen.Settings) },
                        onRecipe = {
                            addHistory(SavedItem.RecipeItem(it))
                            navigate(Screen.RecipeDetail(it))
                        },
                        onRestaurant = {
                            addHistory(SavedItem.RestaurantItem(it))
                            navigate(Screen.RestaurantDetail(it))
                        }
                    )

                    Screen.CookRecommend -> CookRecommendScreen(
                        state = uiState,
                        listState = cookListState,
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

                    is Screen.RecipeDetail -> {
                        LaunchedEffect(screen.id) {
                            loadRecipeDetailIfNeeded(screen.id)
                        }
                        RecipeDetailScreen(
                            recipe = findRecipe(screen.id, uiState) ?: MockData.recipes.first(),
                            isSaved = screen.id in uiState.savedRecipeIds,
                            onBack = ::goBack,
                            onToggleSave = { recipeId -> toggleRecipe(recipeId) }
                        )
                    }

                    Screen.NearbyRestaurant -> NearbyRestaurantScreen(
                        state = uiState,
                        listState = nearbyListState,
                        restaurants = uiState.restaurants,
                        onStateChange = { uiState = it },
                        onBack = ::goBack,
                        onRestaurant = {
                            addHistory(SavedItem.RestaurantItem(it))
                            navigate(Screen.RestaurantDetail(it))
                        },
                        onToggleRestaurant = ::toggleRestaurant,
                        onLocateIssue = ::requestCurrentLocation,
                        onManualLocationSearch = ::applyManualRestaurantLocation,
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
                        listState = savedListState,
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
                text = { Text(dialog.message, color = ChiDianColors.Muted) },
                confirmButton = {
                    Button(
                        onClick = { uiState = uiState.copy(dialog = null) },
                        colors = ButtonDefaults.buttonColors(containerColor = ChiDianColors.ActionPrimary)
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

fun toggleOrderedSaveIds(source: List<String>, id: String): List<String> {
    return if (id in source) {
        source.filterNot { it == id }
    } else {
        listOf(id) + source
    }
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
        RestaurantSortMode.Relevance -> "综合推荐"
        RestaurantSortMode.Distance -> "距离最近"
        RestaurantSortMode.Rating -> "评分最高"
    }
}

fun isLazyItemMeaningfullyVisible(
    listState: LazyListState,
    index: Int,
    minimumVisiblePx: Int = 96
): Boolean {
    val layoutInfo = listState.layoutInfo
    val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return false
    val visibleTop = maxOf(item.offset, layoutInfo.viewportStartOffset)
    val visibleBottom = minOf(item.offset + item.size, layoutInfo.viewportEndOffset)
    val visibleHeight = (visibleBottom - visibleTop).coerceAtLeast(0)
    val threshold = minOf(minimumVisiblePx, (item.size / 2).coerceAtLeast(1))
    return visibleHeight >= threshold
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
