package com.dinnerplan.server

import com.dinnerplan.shared.CookRecommendationResponse
import com.dinnerplan.shared.CookSourceDto
import com.dinnerplan.shared.DishItemDto
import com.dinnerplan.shared.MealPlanDto
import com.dinnerplan.shared.RecipeDatabaseFilter
import com.dinnerplan.shared.RecipeDto
import com.dinnerplan.shared.RecommendationModeDto
import com.dinnerplan.shared.RecommendationRequest
import com.dinnerplan.shared.RestaurantRecommendationResponse

class RecommendationService(
    private val seedRepository: SeedRepository,
    private val aiService: AiService,
    private val amapService: AmapService,
    private val recipeCorpusRepository: RecipeCorpusRepository
) {
    private val spicyMeatDishes = listOf(
        DishItemDto("荤菜", "青椒鸡丁", "微辣高蛋白，20 分钟", "Meat", "recipe_chicken_pepper"),
        DishItemDto("荤菜", "蒜香小炒肉", "下饭肉菜，少油快炒", "Meat"),
        DishItemDto("荤菜", "孜然牛肉", "香气足，适合配米饭", "Meat"),
        DishItemDto("荤菜", "剁椒蒸鱼片", "蒸制少油，辣度可控", "Meat")
    )

    private val nonSpicyMeatDishes = listOf(
        DishItemDto("荤菜", "香煎鸡腿排", "不辣高蛋白，25 分钟", "Meat"),
        DishItemDto("荤菜", "番茄牛肉片", "酸甜开胃，20 分钟", "Meat", "recipe_beef_tomato"),
        DishItemDto("荤菜", "葱姜滑鱼片", "清爽少油，18 分钟", "Meat"),
        DishItemDto("荤菜", "蒜蓉虾仁", "鲜香不辣，15 分钟", "Meat")
    )

    private val spicyVegDishes = listOf(
        DishItemDto("素菜", "酸辣土豆丝", "爽脆开胃，15 分钟", "Veg", "recipe_sour_spicy_potato"),
        DishItemDto("素菜", "虎皮青椒", "椒香下饭，12 分钟", "Veg"),
        DishItemDto("素菜", "手撕包菜", "微辣快炒，10 分钟", "Veg")
    )

    private val nonSpicyVegDishes = listOf(
        DishItemDto("素菜", "清炒西兰花", "清爽少油，10 分钟", "Veg"),
        DishItemDto("素菜", "蚝油生菜", "不辣快手，8 分钟", "Veg"),
        DishItemDto("素菜", "香菇青菜", "鲜香清淡，12 分钟", "Veg")
    )

    private val soupDishes = listOf(
        DishItemDto("汤", "番茄豆腐汤", "清爽收尾，少油暖胃", "Soup"),
        DishItemDto("汤", "紫菜蛋花汤", "5 分钟补汤水", "Soup"),
        DishItemDto("汤", "冬瓜虾皮汤", "清淡解腻，12 分钟", "Soup")
    )

    private val shoppingItemsByDish = mapOf(
        "青椒鸡丁" to listOf("鸡胸肉 250g", "青椒 2 个", "蒜、生抽、淀粉"),
        "蒜香小炒肉" to listOf("五花肉 150g", "蒜苗 1 把", "生抽、料酒"),
        "孜然牛肉" to listOf("牛肉片 200g", "洋葱半个", "孜然粉"),
        "剁椒蒸鱼片" to listOf("鱼片 250g", "剁椒少许", "姜葱"),
        "香煎鸡腿排" to listOf("鸡腿肉 2 块", "黑胡椒", "生抽"),
        "番茄牛肉片" to listOf("牛肉片 200g", "番茄 2 个", "金针菇 1 把"),
        "葱姜滑鱼片" to listOf("鱼片 250g", "葱姜", "淀粉"),
        "蒜蓉虾仁" to listOf("虾仁 200g", "蒜末", "西兰花少许"),
        "酸辣土豆丝" to listOf("土豆 2 个", "白醋", "干辣椒"),
        "虎皮青椒" to listOf("青椒 4 个", "蒜末", "生抽"),
        "手撕包菜" to listOf("包菜半颗", "蒜末", "干辣椒"),
        "清炒西兰花" to listOf("西兰花 1 颗", "蒜末", "盐"),
        "蚝油生菜" to listOf("生菜 1 把", "蚝油", "蒜末"),
        "香菇青菜" to listOf("青菜 1 把", "香菇 6 朵", "生抽"),
        "番茄豆腐汤" to listOf("番茄 2 个", "豆腐 1 盒", "葱花"),
        "紫菜蛋花汤" to listOf("紫菜少许", "鸡蛋 2 个", "葱花"),
        "冬瓜虾皮汤" to listOf("冬瓜 300g", "虾皮少许", "葱花"),
        "米饭 / 杂粮饭" to listOf("大米或杂粮 2-3 人份"),
        "杂粮饭" to listOf("杂粮米 2-3 人份"),
        "清汤面 / 葱油面" to listOf("面条 2 人份", "小葱", "鸡蛋")
    )

    suspend fun recommendCook(request: RecommendationRequest): CookRecommendationResponse {
        return when (request.cookSource) {
            CookSourceDto.DATABASE -> recommendCookFromDatabase(request)
            CookSourceDto.AI_GENERATED -> recommendCookFromAi(request)
        }
    }

    private fun recommendCookFromDatabase(request: RecommendationRequest): CookRecommendationResponse {
        val corpusSearch = if (request.broadSearch) {
            recipeCorpusRepository.randomSample(limit = 48)
        } else {
            recipeCorpusRepository.search(request.query, limit = 48)
        }
        val filteredRecipes = if (request.broadSearch) {
            corpusSearch.recipes.filterNot { recipe ->
                RecipeDatabaseFilter.containsAvoidTerm(recipe, request.preferences.avoids)
            }
        } else {
            RecipeDatabaseFilter.filterAndSort(
                recipes = corpusSearch.recipes,
                query = request.query,
                preferences = request.preferences
            )
        }
        if (filteredRecipes.isNotEmpty()) {
            val comboIntent = isComboIntent(request, aiIntent = null)
            val mealPlans = if (comboIntent) {
                listOf(buildCorpusMealPlan(request, filteredRecipes))
            } else {
                emptyList()
            }
            return CookRecommendationResponse(
                intent = if (comboIntent) RecommendationModeDto.RECIPE_COMBO else RecommendationModeDto.RECIPE_SINGLE,
                summary = if (request.broadSearch) {
                    "已从本地菜谱库随机扩大候选范围，并避开你的忌口。"
                } else {
                    "已从本地菜谱库按“${request.query}”搜索，结果按星级优先排序。"
                },
                mealPlans = mealPlans,
                recipes = filteredRecipes,
                source = CookSourceDto.DATABASE,
                totalMatches = filteredRecipes.size
            )
        }

        if (request.broadSearch) {
            val corpusReady = recipeCorpusRepository.status().ready
            return CookRecommendationResponse(
                intent = request.mode,
                summary = "菜谱数据库暂时没有可用候选。",
                mealPlans = emptyList(),
                recipes = emptyList(),
                fallbackReason = if (corpusReady) {
                    "菜谱数据库中暂无符合忌口条件的随机候选，请调整忌口后再试。"
                } else {
                    "菜谱数据库尚未导入或当前不可用，请检查数据库服务。"
                },
                source = CookSourceDto.DATABASE,
                totalMatches = 0
            )
        }

        val fallback = localCookRecommendation(
            request = request,
            aiIntent = null,
            source = CookSourceDto.DATABASE,
            fallbackReason = if (recipeCorpusRepository.status().ready) {
                if (request.broadSearch) "本地菜谱库暂无可用随机候选，已使用基础菜谱库推荐。"
                else "本地菜谱库未找到匹配结果，已使用基础菜谱库推荐。"
            } else {
                "本地菜谱库尚未导入，已使用基础菜谱库推荐。"
            }
        )
        return fallback
    }

    private suspend fun recommendCookFromAi(request: RecommendationRequest): CookRecommendationResponse {
        val generated = aiService.generateCookRecommendation(
            query = request.query,
            mode = request.mode,
            preferences = request.preferences
        )
        if (generated != null) return generated

        val corpusSearch = recipeCorpusRepository.search(request.query, limit = 24)
        val filteredRecipes = RecipeDatabaseFilter.filterAndSort(
            recipes = corpusSearch.recipes,
            query = request.query,
            preferences = request.preferences
        )
        if (filteredRecipes.isNotEmpty()) {
            val comboIntent = isComboIntent(request, aiIntent = null)
            return CookRecommendationResponse(
                intent = if (comboIntent) RecommendationModeDto.RECIPE_COMBO else RecommendationModeDto.RECIPE_SINGLE,
                summary = "AI 暂时不可用，已改用本地菜谱库搜索。",
                mealPlans = if (comboIntent) listOf(buildCorpusMealPlan(request, filteredRecipes)) else emptyList(),
                recipes = filteredRecipes,
                fallbackReason = "AI 未配置或请求失败，已改用本地菜谱库结果。",
                source = CookSourceDto.AI_GENERATED,
                totalMatches = filteredRecipes.size
            )
        }

        val aiIntent = aiService.parseCookIntent(request.query)
        return localCookRecommendation(
            request = request,
            aiIntent = aiIntent,
            source = CookSourceDto.AI_GENERATED,
            fallbackReason = "AI 未配置或请求失败，且本地菜谱库暂无匹配，已使用基础菜谱库推荐。"
        )
    }

    private fun localCookRecommendation(
        request: RecommendationRequest,
        aiIntent: AiIntent?,
        source: CookSourceDto,
        fallbackReason: String?
    ): CookRecommendationResponse {
        val query = buildString {
            append(request.query)
            append(" ")
            append(request.preferences.tastes.joinToString(" "))
            append(" ")
            append(aiIntent?.keywords.orEmpty().joinToString(" "))
            append(" ")
            append(aiIntent?.taste.orEmpty().joinToString(" "))
        }

        val seedMealPlans = seedRepository.mealPlans
            .filterNot { plan -> RecipeDatabaseFilter.containsAvoidTerm(plan, request.preferences.avoids) }
            .sortedByDescending { plan -> score(plan.tags + plan.title + plan.reason, query) }
        val recipes = RecipeDatabaseFilter.filterAndSort(seedRepository.recipes, request.query, request.preferences)
        val comboIntent = isComboIntent(request, aiIntent)
        val mealPlans = if (comboIntent) {
            val adaptivePlan = buildAdaptiveMealPlan(request, aiIntent)
            listOf(adaptivePlan) + seedMealPlans.filterNot { it.id == adaptivePlan.id }
        } else {
            seedMealPlans
        }

        return CookRecommendationResponse(
            intent = if (comboIntent) RecommendationModeDto.RECIPE_COMBO else RecommendationModeDto.RECIPE_SINGLE,
            summary = aiIntent?.summary?.takeIf { it.isNotBlank() } ?: localCookSummary(request.query),
            mealPlans = mealPlans,
            recipes = recipes,
            fallbackReason = fallbackReason,
            source = source,
            totalMatches = recipes.size + mealPlans.size
        )
    }

    suspend fun recommendRestaurants(request: RecommendationRequest): RestaurantRecommendationResponse {
        val radius = parseRadiusMeters(request.query, request.preferences.defaultDistanceKm)
        val limit = request.preferences.restaurantResultLimit.coerceIn(1, 50)
        val keywordPlan = if (request.broadSearch) {
            broadRestaurantKeywordPlan()
        } else {
            aiService.parseRestaurantKeywordPlan(request.query)
                ?: RestaurantKeywordAiParser.fallbackPlan(request.query)
        }
        val result = amapService.searchRestaurantCandidates(
            plan = keywordPlan,
            location = request.location,
            radiusMeters = radius
        )
        if (result.candidates.isEmpty()) {
            return RestaurantRecommendationResponse(
                restaurants = emptyList(),
                locationUsed = result.locationUsed,
                fallbackReason = result.fallbackReason
            )
        }
        val rerank = RestaurantAiReranker.fallbackRerank(
            query = request.query,
            plan = keywordPlan,
            candidates = result.candidates,
            limit = limit
        )

        return RestaurantRecommendationResponse(
            restaurants = RestaurantAiReranker.toRestaurants(result.candidates, rerank, limit),
            locationUsed = result.locationUsed,
            fallbackReason = result.fallbackReason
        )
    }

    private fun score(fields: List<String>, query: String): Int {
        val haystack = fields.joinToString(" ")
        return query.split(Regex("\\s+|，|,|、"))
            .filter { it.isNotBlank() }
            .fold(0) { total, token -> total + if (haystack.contains(token)) 2 else 0 }
    }

    private fun isComboIntent(request: RecommendationRequest, aiIntent: AiIntent?): Boolean {
        return request.query.contains("两荤") ||
            request.query.contains("三荤") ||
            request.query.contains("四荤") ||
            request.query.contains("一汤") ||
            request.query.contains("主食") ||
            request.mode == RecommendationModeDto.RECIPE_COMBO && request.query.contains("菜单") ||
            aiIntent?.intent == "recipe_combo"
    }

    private fun buildCorpusMealPlan(request: RecommendationRequest, recipes: List<RecipeDto>): MealPlanDto {
        val meatCount = parseCourseCount(request.query, "荤", default = 2).coerceIn(1, 4)
        val vegCount = parseCourseCount(request.query, "素", default = 1).coerceIn(0, 3)
        val soupCount = if (request.query.contains("汤")) 1 else 0
        val includeStaple = request.query.contains("主食") || request.query.contains("米饭") || request.query.contains("面")
        val structure = buildStructure(meatCount, vegCount, soupCount, includeStaple)
        val selected = selectCorpusDishes(recipes, meatCount, vegCount, soupCount, includeStaple)
        return MealPlanDto(
            id = "corpus_meal_${request.query.hashCode().toString().replace("-", "n")}",
            title = if (request.broadSearch) "随机推荐组合菜单" else "本地菜谱库 $structure",
            structure = structure,
            cookTime = estimateCookTime(selected.size, request.query.contains("不辣") || request.query.contains("清淡")),
            servings = "2-3 人份",
            coverUrl = recipes.firstOrNull()?.coverUrl.orEmpty(),
            tags = buildList {
                add("本地菜谱库")
                if (request.broadSearch) {
                    add("随机推荐")
                } else {
                    add("${numberText(meatCount)}荤${numberText(vegCount)}素")
                    if (soupCount > 0) add("一汤")
                    if (includeStaple) add("主食")
                }
            },
            reason = if (request.broadSearch) {
                "从本地菜谱库中扩大候选范围随机组装，并避开你的忌口。"
            } else {
                "从本地菜谱库中按“${request.query}”匹配菜品，并优先选择星级更高的菜谱组成菜单。"
            },
            dishes = selected,
            shoppingList = recipes.flatMap { recipe -> recipe.ingredients.take(4).map { it.name } }.distinct().take(24),
            timeline = listOf(
                "先处理需要腌制、焯水或长时间蒸煮的菜。",
                "再集中切配素菜和汤料，主食提前准备。",
                "按汤或主食、荤菜、素菜的顺序推进，最后集中调味上桌。"
            )
        )
    }

    private fun selectCorpusDishes(
        recipes: List<RecipeDto>,
        meatCount: Int,
        vegCount: Int,
        soupCount: Int,
        includeStaple: Boolean
    ): List<DishItemDto> {
        val used = mutableSetOf<String>()
        fun pick(course: String, badge: String, count: Int, predicate: (RecipeDto) -> Boolean): List<DishItemDto> {
            return recipes.filter { it.id !in used && predicate(it) }
                .take(count)
                .onEach { used += it.id }
                .map { recipe ->
                    DishItemDto(
                        course = course,
                        name = recipe.name,
                        note = "${recipe.ratingStars?.let { "%.1f 星 · ".format(it) }.orEmpty()}${recipe.cookTime}",
                        badge = badge,
                        recipeId = recipe.id
                    )
                }
        }
        val dishes = buildList {
            addAll(pick("荤菜", "Meat", meatCount, ::looksLikeMeatDish))
            addAll(pick("素菜", "Veg", vegCount, ::looksLikeVegDish))
            addAll(pick("汤", "Soup", soupCount, ::looksLikeSoupDish))
            if (includeStaple) addAll(pick("主食", "Staple", 1, ::looksLikeStapleDish))
        }.toMutableList()
        recipes.filter { it.id !in used }
            .take((meatCount + vegCount + soupCount + if (includeStaple) 1 else 0 - dishes.size).coerceAtLeast(0))
            .forEach { recipe ->
                used += recipe.id
                dishes += DishItemDto(
                    course = "菜品",
                    name = recipe.name,
                    note = "${recipe.ratingStars?.let { "%.1f 星 · ".format(it) }.orEmpty()}本地库匹配",
                    badge = "Meat",
                    recipeId = recipe.id
                )
            }
        return dishes
    }

    private fun looksLikeMeatDish(recipe: RecipeDto): Boolean {
        val text = recipeText(recipe)
        return listOf("肉", "鸡", "鸭", "鱼", "虾", "牛", "羊", "排骨", "肥牛", "猪", "蛋").any(text::contains)
    }

    private fun looksLikeVegDish(recipe: RecipeDto): Boolean {
        val text = recipeText(recipe)
        val veg = listOf("素", "青菜", "白菜", "生菜", "土豆", "豆腐", "茄子", "西兰花", "黄瓜", "菌菇")
        return veg.any(text::contains) && !looksLikeMeatDish(recipe)
    }

    private fun looksLikeSoupDish(recipe: RecipeDto): Boolean {
        val text = recipeText(recipe)
        return listOf("汤", "羹", "煲").any(text::contains)
    }

    private fun looksLikeStapleDish(recipe: RecipeDto): Boolean {
        val text = recipeText(recipe)
        return listOf("饭", "面", "粥", "饺", "包子", "馒头", "饼", "粉").any(text::contains)
    }

    private fun recipeText(recipe: RecipeDto): String {
        return listOf(recipe.name, recipe.cuisine, recipe.tags.joinToString(" "), recipe.ingredients.joinToString(" ") { it.name })
            .joinToString(" ")
    }

    private fun buildAdaptiveMealPlan(request: RecommendationRequest, aiIntent: AiIntent?): MealPlanDto {
        val intentText = listOf(
            request.query,
            aiIntent?.summary.orEmpty(),
            aiIntent?.keywords.orEmpty().joinToString(" "),
            aiIntent?.taste.orEmpty().joinToString(" ")
        ).joinToString(" ")
        val notSpicy = intentText.contains("不辣") || intentText.contains("清淡") || intentText.contains("少油")
        val tasteLabel = when {
            notSpicy -> "不辣"
            intentText.contains("麻辣") -> "麻辣"
            intentText.contains("微辣") || intentText.contains("辣") -> "微辣"
            else -> "家常"
        }
        val meatCount = parseCourseCount(intentText, "荤", default = 2).coerceIn(1, 4)
        val vegCount = parseCourseCount(intentText, "素", default = 1).coerceIn(0, 3)
        val soupCount = if (intentText.contains("汤")) 1 else 0
        val includeStaple = intentText.contains("主食") ||
            intentText.contains("米饭") ||
            intentText.contains("面") ||
            intentText.contains("饭")
        val structure = buildStructure(meatCount, vegCount, soupCount, includeStaple)
        val dishes = buildList {
            addAll(pickDishes(if (notSpicy) nonSpicyMeatDishes else spicyMeatDishes, meatCount))
            addAll(pickDishes(if (notSpicy) nonSpicyVegDishes else spicyVegDishes, vegCount))
            addAll(pickDishes(soupDishes, soupCount))
            if (includeStaple) add(stapleDish(intentText))
        }
        val minutes = Regex("(\\d+)\\s*分").find(intentText)?.groupValues?.getOrNull(1)
        val cookTime = minutes?.let { "约 $it 分钟" } ?: estimateCookTime(dishes.size, notSpicy)
        val servings = when {
            intentText.contains("一人") || intentText.contains("一个人") -> "1 人份"
            intentText.contains("四人") || intentText.contains("4人") -> "3-4 人份"
            else -> "2-3 人份"
        }

        return MealPlanDto(
            id = "adaptive_${meatCount}_${vegCount}_${tasteLabel.hashCode().toString().replace("-", "n")}",
            title = "$tasteLabel$structure 晚餐",
            structure = structure.replace("一汤", "一汤").replace("主食", "主食"),
            cookTime = cookTime,
            servings = servings,
            coverUrl = if (notSpicy) {
                "https://images.unsplash.com/photo-1512058564366-18510be2db19?auto=format&fit=crop&w=900&q=80"
            } else {
                "https://images.unsplash.com/photo-1559847844-5315695dadae?auto=format&fit=crop&w=900&q=80"
            },
            tags = buildList {
                add("${numberText(meatCount)}荤${numberText(vegCount)}素")
                if (soupCount > 0) add("一汤")
                if (includeStaple) add("主食")
                add(tasteLabel)
            },
            reason = "按“${request.query}”拆解菜品数量和口味，优先给出与搜索词一致的组合菜单。",
            dishes = dishes,
            shoppingList = buildShoppingList(dishes),
            timeline = buildTimeline(dishes, cookTime)
        )
    }

    private fun parseCourseCount(text: String, course: String, default: Int): Int {
        val digitMatch = Regex("(\\d+)\\s*$course").find(text)
        if (digitMatch != null) return digitMatch.groupValues[1].toIntOrNull() ?: default
        return when {
            text.contains("四$course") -> 4
            text.contains("三$course") -> 3
            text.contains("两$course") || text.contains("二$course") -> 2
            text.contains("一$course") -> 1
            else -> default
        }
    }

    private fun buildStructure(meatCount: Int, vegCount: Int, soupCount: Int, includeStaple: Boolean): String {
        return buildList {
            add("${numberText(meatCount)}荤${numberText(vegCount)}素")
            if (soupCount > 0) add("一汤")
            if (includeStaple) add("主食")
        }.joinToString(" · ")
    }

    private fun numberText(value: Int): String {
        return when (value) {
            0 -> "零"
            1 -> "一"
            2 -> "两"
            3 -> "三"
            4 -> "四"
            else -> value.toString()
        }
    }

    private fun pickDishes(pool: List<DishItemDto>, count: Int): List<DishItemDto> {
        if (count <= 0) return emptyList()
        return List(count) { index -> pool[index % pool.size] }
    }

    private fun stapleDish(text: String): DishItemDto {
        return when {
            text.contains("面") -> DishItemDto("主食", "清汤面 / 葱油面", "按口味选清淡或葱香，10 分钟", "Staple")
            text.contains("杂粮") -> DishItemDto("主食", "杂粮饭", "提前煮好，饱腹更稳", "Staple")
            else -> DishItemDto("主食", "米饭 / 杂粮饭", "提前煮好，适配多数家常菜", "Staple")
        }
    }

    private fun estimateCookTime(dishCount: Int, notSpicy: Boolean): String {
        val minutes = (20 + dishCount * 5 + if (notSpicy) 0 else 5).coerceAtMost(60)
        return "约 $minutes 分钟"
    }

    private fun buildShoppingList(dishes: List<DishItemDto>): List<String> {
        return dishes.flatMap { dish ->
            shoppingItemsByDish[dish.name] ?: listOf(dish.name)
        }.distinct()
    }

    private fun buildTimeline(dishes: List<DishItemDto>, cookTime: String): List<String> {
        val meatNames = dishes.filter { it.course == "荤菜" }.joinToString("、") { it.name }
        val vegNames = dishes.filter { it.course == "素菜" }.joinToString("、") { it.name }
        return listOf(
            "先处理主食和需要腌制的食材，荤菜切配后用少量盐、生抽或淀粉抓匀。",
            "再洗切素菜和汤料，先做耗时较长的汤或主食。",
            "按素菜、荤菜、汤的顺序集中出锅，${listOf(meatNames, vegNames).filter { it.isNotBlank() }.joinToString("、")}尽量在最后 20 分钟完成。",
            "整套菜单预计$cookTime，热菜集中上桌口感更好。"
        )
    }

    private fun localCookSummary(query: String): String {
        return when {
            query.contains("两荤") || query.contains("一汤") || query.contains("主食") ->
                "你需要一套包含荤菜、素菜、汤和主食的晚餐组合，口味会优先按输入匹配。"
            query.contains("清淡") ->
                "你想要清淡少油、操作简单的在家晚餐。"
            else ->
                "已根据输入从基础菜谱库中筛选适合在家做的推荐。"
        }
    }

    private fun parseRadiusMeters(query: String, defaultDistanceKm: Int): Int {
        val match = Regex("(\\d+)\\s*km", RegexOption.IGNORE_CASE).find(query)
        val km = match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: defaultDistanceKm
        return (km * 1000).coerceIn(500, 10000)
    }
}
