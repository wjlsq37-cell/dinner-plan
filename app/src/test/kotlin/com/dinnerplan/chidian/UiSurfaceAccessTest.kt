package com.dinnerplan.chidian

import java.nio.file.Path
import kotlin.random.Random
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
        assertEquals(listOf(RestaurantSortMode.Relevance, RestaurantSortMode.Distance, RestaurantSortMode.Rating), RestaurantSortMode.entries.toList())
        assertTrue(sortModeLabel(RestaurantSortMode.Relevance).contains("综合"))
        assertFalse("人均最低" in RestaurantSortMode.entries.joinToString { sortModeLabel(it) })
    }

    @Test
    fun homeDecisionCardUsesTimeAwareCopyAndDecisionAction() {
        val homeScreen = readProjectFile("app/src/main/java/com/dinnerplan/chidian/ui/screens/HomeScreen.kt")
        val mainActivity = readProjectFile("app/src/main/java/com/dinnerplan/chidian/MainActivity.kt")
        val apiModels = readProjectFile("shared/src/main/kotlin/com/dinnerplan/shared/ApiModels.kt")

        assertTrue(decisionGreetingForHour(8).contains("早"))
        assertTrue(decisionGreetingForHour(12).contains("午"))
        assertTrue(decisionGreetingForHour(19).contains("晚"))
        assertEquals(DecisionTarget.MealPlan("meal_1"), chooseDecisionTarget(listOf("meal_1"), emptyList(), emptyList(), Random(7)))
        assertEquals(DecisionTarget.Recipe("recipe_1"), chooseDecisionTarget(listOf("recipe_1"), emptyList(), Random(7)))
        assertEquals(DecisionTarget.Restaurant("poi_1"), chooseDecisionTarget(emptyList(), listOf("poi_1"), Random(7)))
        assertEquals("recipe_1", chooseDecisionRecipeId(listOf("recipe_1"), Random(7)))
        assertEquals("poi_1", chooseDecisionRestaurantId(listOf("poi_1"), Random(7)))
        assertTrue("DecisionCard(" in homeScreen)
        assertTrue("帮我决定吃什么" in homeScreen)
        assertTrue("正在生成今日灵感..." in homeScreen)
        assertTrue("onDecision" in homeScreen)
        assertTrue("isDecisionLoading: Boolean" in homeScreen)
        assertTrue("DecisionCard(onDecision = onDecision, isDecisionLoading = isDecisionLoading)" in homeScreen)
        assertTrue("enabled = !isDecisionLoading" in homeScreen)
        assertTrue("CircularProgressIndicator" in homeScreen)
        assertTrue("decisionRecipe: Recipe?" in homeScreen)
        assertTrue("decisionRestaurant: Restaurant?" in homeScreen)
        assertTrue("decisionGenerationId: Long" in homeScreen)
        assertTrue("decisionGenerationId = uiState.decisionGenerationId" in mainActivity)
        assertTrue("decisionGenerationId = if (hasDecisionResult)" in mainActivity)
        assertTrue("DecisionRecipeCard(" in homeScreen)
        assertTrue("HOME_TODAY_INSPIRATION_ITEM_INDEX" in mainActivity)
        assertTrue("isLazyItemMeaningfullyVisible(" in mainActivity)
        assertTrue("homeListState.animateScrollToItem(HOME_TODAY_INSPIRATION_ITEM_INDEX)" in mainActivity)
        assertTrue("decisionRecipe = uiState.decisionRecipe" in mainActivity)
        assertTrue("decisionRestaurant = uiState.decisionRestaurant" in mainActivity)
        assertTrue("decisionRecipe = selectedRecipe" in mainActivity)
        assertTrue("decisionRestaurant = selectedRestaurant" in mainActivity)
        assertFalse("val meal = MockData.mealPlans.first()" in homeScreen)
        assertFalse("val restaurant = MockData.restaurants.first()" in homeScreen)
        assertTrue("loadDecisionCandidates(" in mainActivity)
        assertTrue("CookSourceMode.Database" in mainActivity.substringAfter("private suspend fun loadDecisionCandidates"))
        assertTrue("val broadSearch: Boolean = false" in apiModels)
        assertFalse("private const val DECISION_COOK_QUERY = \"晚餐\"" in mainActivity)
        assertTrue("broadSearch = true" in mainActivity.substringAfter("private suspend fun fetchDecisionCookResult"))
        assertFalse("broadSearch = true" in mainActivity.substringAfter("fun refreshCookRecommendations()").substringBefore("fun cancelCookRecommendations()"))
        assertFalse("private const val DECISION_RESTAURANT_QUERY = \"餐厅\"" in mainActivity)
        val decisionRestaurantBody = mainActivity.substringAfter("private suspend fun fetchDecisionRestaurantResult").substringBefore("private fun localMealPlans")
        assertTrue("broadSearch = true" in decisionRestaurantBody)
        val refreshRestaurantsBody = mainActivity.substringAfter("fun refreshRestaurants").substringBefore("fun autoRefreshNearbyRestaurantsIfEmpty")
        assertFalse("broadSearch = true" in refreshRestaurantsBody)
        assertTrue("nearbyRestaurants(" in mainActivity.substringAfter("private suspend fun loadDecisionCandidates"))
        assertFalse("uiState.recipes + uiState.recipeCache + MockData.recipes" in mainActivity.substringAfter("fun decideWhatToEat()").substringBefore("LaunchedEffect"))
        assertFalse("navigate(Screen.RecipeDetail" in mainActivity.substringAfter("fun decideWhatToEat()").substringBefore("LaunchedEffect"))
        assertFalse("navigate(Screen.RestaurantDetail" in mainActivity.substringAfter("fun decideWhatToEat()").substringBefore("LaunchedEffect"))
        assertTrue("fun decideWhatToEat()" in mainActivity)
        assertTrue("Screen.MealPlanDetail" in mainActivity)
        assertTrue("Screen.RecipeDetail" in mainActivity)
        assertTrue("Screen.RestaurantDetail" in mainActivity)
    }

    @Test
    fun homeDecisionOnlyUsesRecipesReturnedByAnApiSource() {
        val builtIn = MockData.recipes.first()
        val apiRecipe = builtIn.copy(id = "mxnzp_123", source = "mxnzp")

        assertEquals(listOf(apiRecipe), apiDecisionRecipes(listOf(builtIn, apiRecipe)))

        val mainActivity = readProjectFile("app/src/main/java/com/dinnerplan/chidian/MainActivity.kt")
        val decisionBody = mainActivity.substringAfter("fun decideWhatToEat()")
            .substringBefore("LaunchedEffect(settingsLoaded)")
        assertTrue("apiDecisionRecipes(candidates.recipes)" in decisionBody)
        assertFalse("candidates.recipes.ifEmpty { nextState.recipes + nextState.recipeCache }" in decisionBody)
    }

    @Test
    fun homeDecisionCardsAnimateAndHighlightOnNewGeneration() {
        val homeScreen = readProjectFile("app/src/main/java/com/dinnerplan/chidian/ui/screens/HomeScreen.kt")
        val animatedCard = homeScreen.substringAfter("private fun AnimatedDecisionCard(")
            .substringBefore("@Composable\nprivate fun DecisionLoadingCard")

        assertTrue("AnimatedDecisionCard(" in homeScreen)
        assertTrue("Animatable" in homeScreen)
        assertTrue("graphicsLayer" in homeScreen)
        assertTrue("translationY" in homeScreen)
        assertTrue("scaleX" in homeScreen)
        assertTrue("scaleY" in homeScreen)
        assertTrue("delayMillis = 0" in homeScreen)
        assertTrue("delayMillis = 130" in homeScreen)
        assertTrue("16.dp.toPx()" in homeScreen)
        assertTrue("highlight.animateTo(0f, tween(250" in homeScreen)
        assertTrue("delay(900)" in homeScreen)
        assertTrue("borderColor" in homeScreen)
        assertTrue("containerColor" in homeScreen)
        assertTrue("ChiDianColors.ActionPrimary.copy(alpha = 0.35f)" in animatedCard)
        assertTrue("val containerColor = ChiDianColors.Surface" in animatedCard)
        assertFalse("ChiDianColors.Mint.copy" in animatedCard)
        assertFalse("lerp(ChiDianColors.Surface" in animatedCard)
    }

    @Test
    fun nearbyScreenUsesApprovedPreviewStructure() {
        val nearbyScreen = readProjectFile("app/src/main/java/com/dinnerplan/chidian/ui/screens/NearbyRestaurantScreen.kt")
        val cardBody = nearbyScreen.substringAfter("private fun NearbyRestaurantCard(")

        assertTrue("NearbyHeroPanel(" in nearbyScreen)
        assertTrue("NearbySearchSurface(" in nearbyScreen)
        assertTrue("NearbySortRow(" in nearbyScreen)
        assertTrue("NearbyNoticeCard(" in nearbyScreen)
        assertTrue("NearbyDistancePill(" in cardBody)
        assertTrue("NearbyReasonPill(" in cardBody)
        assertTrue("NearbyRestaurantActions(" in cardBody)
        assertTrue("查看详情" in nearbyScreen)
        assertTrue("收藏" in nearbyScreen)
        assertTrue("isEditingLocation" in nearbyScreen)
        assertTrue("locationDraft" in nearbyScreen)
        assertTrue("onManualLocationSearch" in nearbyScreen)
        assertTrue("applyManualRestaurantLocation" in readProjectFile("app/src/main/java/com/dinnerplan/chidian/MainActivity.kt"))
        assertFalse("TextButton(onClick = onLocateIssue" in nearbyScreen)
        assertTrue(".weight(1f)" in nearbyScreen.substringAfter("private fun NearbySortRow(").substringBefore("@Composable\nprivate fun NearbyNoticeCard("))
        assertFalse("horizontalScroll" in nearbyScreen.substringAfter("private fun NearbySortRow(").substringBefore("@Composable\nprivate fun NearbyNoticeCard("))
        assertFalse("人均最低" in nearbyScreen)
        assertFalse("FoodScreenHeader(" in nearbyScreen)
        assertFalse("NearbyLocationCard(" in nearbyScreen)
        assertFalse("NearbySortCard(" in nearbyScreen)
        assertFalse("OutlinedTextField(" in nearbyScreen)
    }

    @Test
    fun themeConsistencyUsesRoleColorsInsteadOfGreenPageTheme() {
        val mainActivity = readProjectFile("app/src/main/java/com/dinnerplan/chidian/MainActivity.kt")
        val foodControls = readProjectFile("app/src/main/java/com/dinnerplan/chidian/ui/components/FoodControls.kt")
        val homeScreen = readProjectFile("app/src/main/java/com/dinnerplan/chidian/ui/screens/HomeScreen.kt")
        val nearbyScreen = readProjectFile("app/src/main/java/com/dinnerplan/chidian/ui/screens/NearbyRestaurantScreen.kt")
        val detailScreens = readProjectFile("app/src/main/java/com/dinnerplan/chidian/ui/screens/DetailScreens.kt")
        val savedScreen = readProjectFile("app/src/main/java/com/dinnerplan/chidian/ui/screens/SavedSettingsScreens.kt")

        assertTrue("enum class FoodTone" in foodControls)
        assertTrue("tone: FoodTone = FoodTone.Neutral" in foodControls)
        assertFalse("green: Boolean" in foodControls)

        assertFalse("private val Tomato" in mainActivity)
        assertFalse("private val Muted" in mainActivity)
        assertTrue("ChiDianColors.ActionPrimary" in mainActivity)

        assertTrue("containerColor = ChiDianColors.ActionPrimary" in homeScreen)
        assertTrue("tint = ChiDianColors.LocationAccent" in homeScreen)

        assertTrue("containerColor = ChiDianColors.ActionPrimary" in nearbyScreen)
        assertTrue("cursorBrush = SolidColor(ChiDianColors.LocationAccent)" in nearbyScreen)
        assertTrue("color = ChiDianColors.LocationAccentSoft" in nearbyScreen)
        assertTrue("tint = ChiDianColors.LocationAccent" in nearbyScreen)

        assertFalse("Brush.linearGradient(listOf(ChiDianColors.MintDark, ChiDianColors.Mint))" in detailScreens)
        assertFalse("green: Boolean" in detailScreens)
        assertTrue("containerColor = ChiDianColors.ActionPrimary" in detailScreens)
        assertTrue("tint = ChiDianColors.LocationAccent" in detailScreens)

        assertFalse("green: Boolean" in savedScreen)
        assertTrue("ChiDianColors.LocationAccentSoft" in savedScreen)
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

    @Test
    fun restaurantResultLimitIsFixedAndHiddenFromSettings() {
        val mainActivity = readProjectFile("app/src/main/java/com/dinnerplan/chidian/MainActivity.kt")
        val settingsScreen = readProjectFile("app/src/main/java/com/dinnerplan/chidian/ui/screens/SavedSettingsScreens.kt")
        val directAmap = readProjectFile("app/src/main/java/com/dinnerplan/chidian/DirectAmapApiClient.kt")
        val directAi = readProjectFile("app/src/main/java/com/dinnerplan/chidian/DirectAiApiClient.kt")

        assertTrue("FIXED_RESTAURANT_RESULT_LIMIT = 50" in mainActivity)
        assertTrue("restaurantResultLimit" in mainActivity)
        assertFalse("RestaurantResultLimit" in mainActivity)
        assertFalse("附近吃显示数量" in settingsScreen)
        assertFalse("(1..50)" in settingsScreen)
        assertTrue("parseRestaurantKeywordPlan" in directAi)
        assertTrue("rerankRestaurants" in directAi)
        assertTrue("DirectRestaurantKeywordAiParser.fallbackPlan" in directAmap)
        assertTrue("DirectRestaurantAiReranker.toRestaurants" in directAmap)
        assertFalse("directAiApiClient.rerankRestaurants(" in directAmap)
        assertTrue("DirectRestaurantAiReranker.fallbackRerank" in directAmap)
    }

    @Test
    fun orderedFavoritesPutNewestFirstAndCanRemoveExisting() {
        assertEquals(listOf("new", "old"), toggleOrderedSaveIds(listOf("old"), "new"))
        assertEquals(listOf("old"), toggleOrderedSaveIds(listOf("new", "old"), "new"))
    }

    @Test
    fun savedItemsUseOrderAndVisibleToastFeedback() {
        val mainActivity = readProjectFile("app/src/main/java/com/dinnerplan/chidian/MainActivity.kt")
        val savedScreen = readProjectFile("app/src/main/java/com/dinnerplan/chidian/ui/screens/SavedSettingsScreens.kt")

        assertTrue("SavedOrder" in mainActivity)
        assertTrue("savedOrder" in mainActivity)
        assertTrue("Toast.makeText" in mainActivity)
        assertTrue("保存失败" in mainActivity)
        assertTrue("toggleOrderedSaveIds" in mainActivity)
        assertTrue("state.savedOrder" in savedScreen)
    }

    @Test
    fun developerRecipePageSizeControlsDirectRecipeCandidates() {
        val mainActivity = readProjectFile("app/src/main/java/com/dinnerplan/chidian/MainActivity.kt")
        val methodBody = mainActivity.substringAfter("private suspend fun recommendFromRecipeApi")
            .substringBefore("private class DirectRestaurantRepository")

        assertTrue("developerSettings.safePageSize" in methodBody)
        assertFalse(".take(6)" in methodBody)
    }

    @Test
    fun restaurantResultLimitAllowsFiftyAcrossAppSources() {
        val mainActivity = readProjectFile("app/src/main/java/com/dinnerplan/chidian/MainActivity.kt")
        val settingsScreen = readProjectFile("app/src/main/java/com/dinnerplan/chidian/ui/screens/SavedSettingsScreens.kt")
        val directAmap = readProjectFile("app/src/main/java/com/dinnerplan/chidian/DirectAmapApiClient.kt")
        val directModules = readProjectFile("app/src/main/java/com/dinnerplan/chidian/DirectRestaurantAiModules.kt")
        val sharedModels = readProjectFile("shared/src/main/kotlin/com/dinnerplan/shared/ApiModels.kt")

        assertTrue("restaurantResultLimit = FIXED_RESTAURANT_RESULT_LIMIT" in mainActivity)
        assertFalse("附近吃显示数量" in settingsScreen)
        assertTrue("coerceIn(1, 50)" in directAmap)
        assertTrue("coerceIn(1, 50)" in directModules)
        assertTrue("restaurantResultLimit: Int = 50" in sharedModels)
    }

    @Test
    fun nonDeveloperModeUsesVercelBackendAndDeveloperModeKeepsDirectClients() {
        val mainActivity = readProjectFile("app/src/main/java/com/dinnerplan/chidian/MainActivity.kt")
        val settingsScreen = readProjectFile("app/src/main/java/com/dinnerplan/chidian/ui/screens/SavedSettingsScreens.kt")
        val backendClient = readProjectFile("app/src/main/java/com/dinnerplan/chidian/BackendApiClient.kt")

        assertTrue("DEFAULT_BACKEND_BASE_URL = \"https://dinner-plan.vercel.app\"" in mainActivity)
        assertTrue("val backendBaseUrl: String = DEFAULT_BACKEND_BASE_URL" in mainActivity)
        assertTrue("if (snapshot.developerSettings.enabled) directCookRepository else backendAiRepository" in mainActivity)
        assertTrue("if (snapshot.developerSettings.enabled) directRestaurantRepository else backendRestaurantRepository" in mainActivity)
        assertTrue("线上/本地后端地址" in settingsScreen)
        assertTrue("服务暂时不可用，请稍后再试" in backendClient)
        assertFalse("Ktor" in backendClient)
        assertFalse("本机代理" in backendClient)
    }

    @Test
    fun listScreensKeepLazyListStateForDetailReturnNavigation() {
        val mainActivity = readProjectFile("app/src/main/java/com/dinnerplan/chidian/MainActivity.kt")
        val homeScreen = readProjectFile("app/src/main/java/com/dinnerplan/chidian/ui/screens/HomeScreen.kt")
        val cookScreen = readProjectFile("app/src/main/java/com/dinnerplan/chidian/ui/screens/CookRecommendScreen.kt")
        val nearbyScreen = readProjectFile("app/src/main/java/com/dinnerplan/chidian/ui/screens/NearbyRestaurantScreen.kt")
        val savedScreen = readProjectFile("app/src/main/java/com/dinnerplan/chidian/ui/screens/SavedSettingsScreens.kt")

        assertTrue("rememberLazyListState()" in mainActivity)
        assertTrue("listState = homeListState" in mainActivity)
        assertTrue("listState = cookListState" in mainActivity)
        assertTrue("listState = nearbyListState" in mainActivity)
        assertTrue("listState = savedListState" in mainActivity)
        assertTrue("listState: LazyListState" in homeScreen)
        assertTrue("listState: LazyListState" in cookScreen)
        assertTrue("listState: LazyListState" in nearbyScreen)
        assertTrue("listState: LazyListState" in savedScreen)
        assertTrue("state = listState" in cookScreen)
        assertTrue("animateScrollToItem(0)" in mainActivity)
    }

    @Test
    fun deepSeekIsDefaultDeveloperAiProvider() {
        val settings = DeveloperSettings()
        val developerSettings = readProjectFile("app/src/main/java/com/dinnerplan/chidian/DeveloperSettings.kt")
        val settingsScreen = readProjectFile("app/src/main/java/com/dinnerplan/chidian/ui/screens/SavedSettingsScreens.kt")
        val mainActivity = readProjectFile("app/src/main/java/com/dinnerplan/chidian/MainActivity.kt")

        assertEquals("deepseek", settings.aiProvider)
        assertEquals("https://api.deepseek.com", settings.aiBaseUrl)
        assertEquals("deepseek-v4-flash", settings.aiModel)
        assertTrue("deepseek-v4-pro" in developerSettings)
        assertTrue("AiProvider.DeepSeek" in settingsScreen)
        assertTrue("DeveloperAiProvider" in mainActivity)
    }

    @Test
    fun recipeApiSettingsAreSourceSelectableAndSearchHidesKeyboard() {
        val mainActivity = readProjectFile("app/src/main/java/com/dinnerplan/chidian/MainActivity.kt")
        val developerSettings = readProjectFile("app/src/main/java/com/dinnerplan/chidian/DeveloperSettings.kt")
        val settingsScreen = readProjectFile("app/src/main/java/com/dinnerplan/chidian/ui/screens/SavedSettingsScreens.kt")
        val foodControls = readProjectFile("app/src/main/java/com/dinnerplan/chidian/ui/components/FoodControls.kt")
        val nearbyScreen = readProjectFile("app/src/main/java/com/dinnerplan/chidian/ui/screens/NearbyRestaurantScreen.kt")

        assertTrue("recipeApiSource" in developerSettings)
        assertTrue("recipeApiBaseUrl" in developerSettings)
        assertTrue("MXNZP_RECIPE_API_URL" in developerSettings)
        assertTrue("DeveloperRecipeApiSource" in mainActivity)
        assertTrue("菜谱数据源" in settingsScreen)
        assertTrue("mxnzp" in settingsScreen)
        assertTrue("自定义" in settingsScreen)
        assertTrue("LocalSoftwareKeyboardController" in foodControls)
        assertTrue("clearFocus()" in foodControls)
        assertTrue("LocalSoftwareKeyboardController" in nearbyScreen)
        assertTrue("clearFocus()" in nearbyScreen)
    }

    @Test
    fun defaultSearchInputsAreBlankAndNearbyCardsStayImageFirst() {
        val state = AppUiState()
        val nearbyScreen = readProjectFile("app/src/main/java/com/dinnerplan/chidian/ui/screens/NearbyRestaurantScreen.kt")

        assertEquals("", state.cookQuery)
        assertEquals("", state.restaurantQuery)
        assertTrue("AsyncImage(" in nearbyScreen.substringAfter("private fun NearbyRestaurantCard("))
        assertFalse("NearbyTagRow(restaurant.tags)" in nearbyScreen)
        assertFalse("text = restaurant.address.ifBlank" in nearbyScreen)
    }

    @Test
    fun cookCardsRenderReturnedCoverImagesOnlyWhenAvailable() {
        val cookScreen = readProjectFile("app/src/main/java/com/dinnerplan/chidian/ui/screens/CookRecommendScreen.kt")
        val mealCard = cookScreen.substringAfter("private fun CookMealPlanCard(").substringBefore("@Composable\nprivate fun CookRecipeCard(")
        val recipeCard = cookScreen.substringAfter("private fun CookRecipeCard(").substringBefore("@Composable\nprivate fun DishList(")

        assertTrue("if (plan.coverUrl.isNotBlank())" in mealCard)
        assertTrue("CookCardCover(imageUrl = plan.coverUrl" in mealCard)
        assertTrue("if (recipe.coverUrl.isNotBlank())" in recipeCard)
        assertTrue("CookCardCover(imageUrl = recipe.coverUrl" in recipeCard)
        assertTrue("AsyncImage(" in cookScreen.substringAfter("private fun CookCardCover("))
    }

    @Test
    fun userFacingSurfacesSanitizeFallbacksAndReasons() {
        val messageSanitizer = readProjectFile("app/src/main/java/com/dinnerplan/chidian/UserFacingMessages.kt")
        val cookScreen = readProjectFile("app/src/main/java/com/dinnerplan/chidian/ui/screens/CookRecommendScreen.kt")
        val nearbyScreen = readProjectFile("app/src/main/java/com/dinnerplan/chidian/ui/screens/NearbyRestaurantScreen.kt")
        val detailScreens = readProjectFile("app/src/main/java/com/dinnerplan/chidian/ui/screens/DetailScreens.kt")
        val settingsScreen = readProjectFile("app/src/main/java/com/dinnerplan/chidian/ui/screens/SavedSettingsScreens.kt")

        assertTrue("friendlyStatusMessage" in messageSanitizer)
        assertTrue("friendlyReason" in messageSanitizer)
        assertTrue("Log.w" in messageSanitizer)
        assertTrue("friendlyStatusMessage(reason" in cookScreen)
        assertTrue("friendlyStatusMessage(reason" in nearbyScreen)
        assertTrue("friendlyReason(plan.reason" in cookScreen)
        assertTrue("friendlyReason(recipe.reason" in cookScreen)
        assertTrue("friendlyReason(restaurant.reason" in nearbyScreen)
        assertTrue("friendlyReason(plan.reason" in detailScreens)
        assertTrue("friendlyReason(recipe.reason" in detailScreens)
        assertTrue("friendlyReason(restaurant.reason" in detailScreens)
        assertFalse("message = reason" in cookScreen)
        assertFalse("message = reason" in nearbyScreen)
        assertFalse("text = plan.reason" in cookScreen)
        assertFalse("text = recipe.reason" in cookScreen)
        assertFalse("text = restaurant.reason" in nearbyScreen)
        assertFalse("subtitle = plan.reason" in detailScreens)
        assertFalse("subtitle = recipe.reason" in detailScreens)
        assertFalse("subtitle = restaurant.reason" in detailScreens)
        assertFalse("调试链路" in settingsScreen)
    }

    @Test
    fun cookResultsPersistAndNearbyColdStartUsesGenericRestaurantSearch() {
        val mainActivity = readProjectFile("app/src/main/java/com/dinnerplan/chidian/MainActivity.kt")

        assertTrue("LastCookQuery" in mainActivity)
        assertTrue("LastCookRecipes" in mainActivity)
        assertTrue("LastCookMealPlans" in mainActivity)
        assertTrue("decodeRecipeCache(preferences[SettingsKeys.LastCookRecipes])" in mainActivity)
        assertTrue("decodeMealPlanCache(preferences[SettingsKeys.LastCookMealPlans])" in mainActivity)
        assertTrue("autoRefreshNearbyRestaurantsIfEmpty" in mainActivity)
        assertTrue("query = \"餐厅\"" in mainActivity)
        assertFalse("restaurants = cachedRestaurants" in mainActivity)
    }

    private fun readProjectFile(relativePath: String): String {
        val candidates = listOf(Path.of(relativePath), Path.of("..").resolve(relativePath))
        val path = candidates.firstOrNull { it.toFile().exists() }
            ?: error("Cannot find project file: $relativePath")
        return path.toFile().readText()
    }
}
