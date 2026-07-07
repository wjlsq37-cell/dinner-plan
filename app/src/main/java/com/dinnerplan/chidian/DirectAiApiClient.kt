package com.dinnerplan.chidian

import com.dinnerplan.shared.CookRecommendationResponse
import com.dinnerplan.shared.CookSourceDto
import com.dinnerplan.shared.DishItemDto
import com.dinnerplan.shared.IngredientDto
import com.dinnerplan.shared.MealPlanDto
import com.dinnerplan.shared.RecipeDto
import com.dinnerplan.shared.RecipeDatabaseFilter
import com.dinnerplan.shared.RecommendationModeDto
import com.dinnerplan.shared.UserPreferenceDto
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Protocol

internal class DirectAiApiClient(
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val client = HttpClient(OkHttp) {
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = 20_000
            requestTimeoutMillis = 180_000
            socketTimeoutMillis = 180_000
        }
        engine {
            config {
                retryOnConnectionFailure(true)
                protocols(listOf(Protocol.HTTP_1_1))
            }
        }
    }

    suspend fun generateCookRecommendation(
        settings: DeveloperSettings,
        query: String,
        mode: RecommendationModeDto,
        preferences: UserPreferenceDto
    ): CookRecommendationResponse? {
        if (settings.aiBaseUrl.isBlank() || settings.aiApiKey.isBlank() || settings.aiModel.isBlank()) {
            return null
        }
        val firstPrompt = buildDirectCookPrompt(query, mode, preferences)
        var generation = requestCookGenerationWithFallback(settings, firstPrompt.system, firstPrompt.user).generation
            ?: throw IllegalStateException("AI 未返回可解析的菜谱 JSON。")

        val violatedTerms = generation.violatedAvoidTerms(preferences.avoids)
        if (violatedTerms.isNotEmpty()) {
            val retryPrompt = buildDirectCookPrompt(query, mode, preferences, violatedTerms)
            val retryGeneration = requestCookGenerationWithFallback(settings, retryPrompt.system, retryPrompt.user).generation
            if (retryGeneration != null) {
                generation = retryGeneration
            }
        }
        generation = generation.filterAvoidingTerms(preferences.avoids)

        val recipes = generation.recipes.map { recipe ->
            recipe.copy(
                id = recipe.id.ifBlank { "ai_recipe_${recipe.name.hashCode().toString().replace("-", "n")}" },
                source = "ai_generated"
            )
        }
        val mealPlans = generation.mealPlans.map { plan ->
            plan.copy(id = plan.id.ifBlank { "ai_meal_${plan.title.hashCode().toString().replace("-", "n")}" })
        }
        if (recipes.isEmpty() && mealPlans.isEmpty()) {
            throw IllegalStateException("AI 已响应，但没有生成任何菜谱或组合菜单。")
        }
        return CookRecommendationResponse(
            intent = generation.intent,
            summary = generation.summary.ifBlank { "已根据你的输入生成做饭建议。" },
            mealPlans = mealPlans,
            recipes = recipes,
            source = CookSourceDto.AI_GENERATED,
            totalMatches = mealPlans.size + recipes.size
        )
    }

    suspend fun parseRestaurantKeywordPlan(
        settings: DeveloperSettings,
        query: String
    ): DirectRestaurantKeywordPlan? {
        if (settings.aiBaseUrl.isBlank() || settings.aiApiKey.isBlank() || settings.aiModel.isBlank()) {
            return null
        }
        val system = """
            你是餐厅搜索关键词解析器。只输出 JSON，不要解释。
            JSON 字段必须是：summary、keywords、mustMatch、preferMatch、negativeMatch、searchStrategy。
            keywords 必须是多个可分别用于高德搜索的短关键词，不要把所有词拼成一个长查询。
            只根据用户这次的附近吃需求解析，不要使用任何做饭忌口或做饭偏好。
        """.trimIndent()
        val user = """
            用户附近吃需求：$query
            返回示例：
            {
              "summary": "用户想找日料自助餐厅",
              "keywords": ["日料自助", "日本料理", "寿司", "自助餐"],
              "mustMatch": ["自助"],
              "preferMatch": ["日料", "日本料理", "寿司"],
              "negativeMatch": [],
              "searchStrategy": "separate"
            }
        """.trimIndent()
        val jsonAttempt = requestRestaurantKeywordPlan(settings, system, user, useJsonMode = true)
        val plainAttempt = if (jsonAttempt == null) {
            requestRestaurantKeywordPlan(settings, system, user, useJsonMode = false)
        } else {
            null
        }
        return (jsonAttempt ?: plainAttempt)?.normalized(query)
    }

    suspend fun rerankRestaurants(
        settings: DeveloperSettings,
        query: String,
        plan: DirectRestaurantKeywordPlan,
        candidates: List<DirectRestaurantCandidate>,
        limit: Int
    ): DirectRestaurantRerankResult? {
        if (settings.aiBaseUrl.isBlank() || settings.aiApiKey.isBlank() || settings.aiModel.isBlank() || candidates.isEmpty()) {
            return null
        }
        val boundedCandidates = candidates.take(60)
        val candidatesJson = json.encodeToString(
            ListSerializer(DirectRestaurantCandidate.serializer()),
            boundedCandidates
        )
        val system = """
            你是附近吃餐厅筛选器。只输出 JSON，不要解释。
            你只能从候选餐厅 id 中选择，不能新增、编造或修改餐厅。
            不能改写候选中的名称、地址、距离、评分、人均、电话等原始事实。
            JSON 字段必须是：rankedIds、excluded、matchReasons、riskWarnings。
            rankedIds 只能包含候选 id，按匹配度从高到低排序，最多返回 $limit 个。
        """.trimIndent()
        val user = """
            用户原始需求：$query
            关键词解析：${json.encodeToString(DirectRestaurantKeywordPlan.serializer(), plan.normalized(query))}
            显示数量：${limit.coerceIn(1, 50)}
            候选餐厅 JSON：$candidatesJson
        """.trimIndent()
        val jsonAttempt = requestRestaurantRerank(settings, system, user, useJsonMode = true)
        val plainAttempt = if (jsonAttempt == null) {
            requestRestaurantRerank(settings, system, user, useJsonMode = false)
        } else {
            null
        }
        return (jsonAttempt ?: plainAttempt)?.let {
            DirectRestaurantAiReranker.sanitizeAiResult(it, boundedCandidates, limit)
        }
    }

    private suspend fun requestCookGenerationWithFallback(
        settings: DeveloperSettings,
        system: String,
        user: String
    ): DirectAiAttempt {
        val jsonModeAttempt = requestCookGeneration(settings, system, user, useJsonMode = true)
        if (jsonModeAttempt.generation != null) return jsonModeAttempt
        val plainAttempt = requestCookGeneration(settings, system, user, useJsonMode = false)
        return plainAttempt.generation?.let { plainAttempt }
            ?: run {
                val rawError = plainAttempt.errorMessage ?: jsonModeAttempt.errorMessage
                logInternalIssue("Direct AI cook generation failed", rawError)
                DirectAiAttempt(errorMessage = friendlyStatusMessage(rawError, UserMessageContext.Ai))
            }
    }

    private suspend fun requestRestaurantKeywordPlan(
        settings: DeveloperSettings,
        system: String,
        user: String,
        useJsonMode: Boolean
    ): DirectRestaurantKeywordPlan? {
        return requestChatContent(settings, system, user, temperature = 0.2, useJsonMode = useJsonMode)
            .content
            ?.let { parseDirectRestaurantKeywordPlan(json, it) }
    }

    private suspend fun requestRestaurantRerank(
        settings: DeveloperSettings,
        system: String,
        user: String,
        useJsonMode: Boolean
    ): DirectRestaurantRerankResult? {
        return requestChatContent(settings, system, user, temperature = 0.1, useJsonMode = useJsonMode)
            .content
            ?.let { parseDirectRestaurantRerank(json, it) }
    }

    private suspend fun requestChatContent(
        settings: DeveloperSettings,
        system: String,
        user: String,
        temperature: Double,
        useJsonMode: Boolean
    ): DirectTextAttempt {
        return try {
            withTimeout(settings.maxWaitMillis) {
                val bodyText = if (useJsonMode) {
                    json.encodeToString(
                        DirectChatCompletionRequest.serializer(),
                        DirectChatCompletionRequest(
                            model = settings.aiModel,
                            messages = directChatMessages(system, user),
                            temperature = temperature,
                            responseFormat = DirectResponseFormat("json_object")
                        )
                    )
                } else {
                    json.encodeToString(
                        DirectPlainChatCompletionRequest.serializer(),
                        DirectPlainChatCompletionRequest(
                            model = settings.aiModel,
                            messages = directChatMessages(system, user),
                            temperature = temperature
                        )
                    )
                }
                val response = client.post(chatCompletionsUrl(settings.aiBaseUrl)) {
                    bearerAuth(settings.aiApiKey)
                    contentType(ContentType.Application.Json)
                    setBody(bodyText)
                }
                val text = response.bodyAsText()
                if (!response.status.isSuccess()) {
                    return@withTimeout DirectTextAttempt(
                        errorMessage = "AI 接口返回 ${response.status.value}：${text.readableSnippet()}"
                    )
                }
                val content = runCatching {
                    json.decodeFromString(DirectChatCompletionResponse.serializer(), text)
                }.getOrElse { error ->
                    return@withTimeout DirectTextAttempt(
                        errorMessage = "AI 响应不是 OpenAI 兼容格式：${error.message ?: text.readableSnippet()}"
                    )
                }
                    .choices
                    .firstOrNull()
                    ?.message
                    ?.content
                    .orEmpty()
                if (content.isBlank()) {
                    DirectTextAttempt(errorMessage = "AI 响应内容为空。")
                } else {
                    DirectTextAttempt(content = content)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            DirectTextAttempt(errorMessage = error.message ?: "AI 请求异常。")
        }
    }

    private suspend fun requestCookGeneration(
        settings: DeveloperSettings,
        system: String,
        user: String,
        useJsonMode: Boolean
    ): DirectAiAttempt {
        return try {
            withTimeout(settings.maxWaitMillis) {
                val bodyText = if (useJsonMode) {
                    json.encodeToString(
                        DirectChatCompletionRequest.serializer(),
                        DirectChatCompletionRequest(
                            model = settings.aiModel,
                            messages = directChatMessages(system, user),
                            temperature = 0.6,
                            responseFormat = DirectResponseFormat("json_object")
                        )
                    )
                } else {
                    json.encodeToString(
                        DirectPlainChatCompletionRequest.serializer(),
                        DirectPlainChatCompletionRequest(
                            model = settings.aiModel,
                            messages = directChatMessages(system, user),
                            temperature = 0.6
                        )
                    )
                }
                val response = client.post(chatCompletionsUrl(settings.aiBaseUrl)) {
                    bearerAuth(settings.aiApiKey)
                    contentType(ContentType.Application.Json)
                    setBody(bodyText)
                }
                val text = response.bodyAsText()
                if (!response.status.isSuccess()) {
                    return@withTimeout DirectAiAttempt(
                        errorMessage = "AI 接口返回 ${response.status.value}：${text.readableSnippet()}"
                    )
                }
                val content = runCatching {
                    json.decodeFromString(DirectChatCompletionResponse.serializer(), text)
                }.getOrElse { error ->
                    return@withTimeout DirectAiAttempt(
                        errorMessage = "AI 响应不是 OpenAI 兼容格式：${error.message ?: text.readableSnippet()}"
                    )
                }
                    .choices
                    .firstOrNull()
                    ?.message
                    ?.content
                    .orEmpty()
                if (content.isBlank()) {
                    return@withTimeout DirectAiAttempt(errorMessage = "AI 响应内容为空。")
                }
                DirectAiAttempt(
                    generation = parseDirectAiCookGeneration(json, content),
                    errorMessage = "AI 返回内容不是可解析菜谱 JSON：${content.readableSnippet()}"
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            DirectAiAttempt(errorMessage = error.message ?: "AI 请求异常。")
        }
    }

    private fun chatCompletionsUrl(baseUrl: String): String {
        val normalized = baseUrl.trim().trimEnd('/')
        return if (normalized.endsWith("/chat/completions")) normalized else "$normalized/chat/completions"
    }

    private fun directChatMessages(system: String, user: String): List<DirectChatMessage> {
        return listOf(DirectChatMessage("system", system), DirectChatMessage("user", user))
    }
}

private data class DirectAiAttempt(
    val generation: DirectAiCookGeneration? = null,
    val errorMessage: String? = null
)

private data class DirectTextAttempt(
    val content: String? = null,
    val errorMessage: String? = null
)

private data class DirectCookPrompt(
    val system: String,
    val user: String
)

@Serializable
private data class DirectAiCookGeneration(
    val intent: RecommendationModeDto = RecommendationModeDto.RECIPE_SINGLE,
    val summary: String = "",
    val mealPlans: List<MealPlanDto> = emptyList(),
    val recipes: List<RecipeDto> = emptyList()
)

private fun buildDirectCookPrompt(
    query: String,
    mode: RecommendationModeDto,
    preferences: UserPreferenceDto,
    retryAvoidTerms: List<String> = emptyList()
): DirectCookPrompt {
    val modeText = if (mode == RecommendationModeDto.RECIPE_COMBO) "组合菜单" else "单道菜"
    val system = """
        你是《吃点啥》的做饭推荐助手。只输出 JSON，不要解释。
        JSON 字段必须是：intent、summary、mealPlans、recipes。
        intent 只能是 RECIPE_SINGLE 或 RECIPE_COMBO。
        mealPlans 数组项字段：id,title,structure,cookTime,servings,coverUrl,tags,reason,dishes,shoppingList,timeline。
        dishes 数组项字段：course,name,note,badge,recipeId；badge 只能是 Meat、Veg、Soup、Staple。
        recipes 数组项字段：id,name,cuisine,taste,tags,difficulty,cookTime,servings,coverUrl,reason,ingredients,steps,tips。
        ingredients 数组项字段：name,amount。
        coverUrl 可用空字符串；不要编造餐厅，只生成做饭菜谱。
        必须避开用户设置的忌口食材，菜名、食材、步骤、描述里都不能出现这些食材。
        尽量符合用户设置的偏好口味，但偏好不能覆盖忌口。
    """.trimIndent()
    val retryLine = if (retryAvoidTerms.isEmpty()) {
        ""
    } else {
        "上一次结果命中忌口：${retryAvoidTerms.joinToString("、")}。请重新生成，彻底避开这些词。"
    }
    val user = """
        用户需求：$query
        推荐类型：$modeText
        偏好口味：${preferences.tastes.joinToString("、").ifBlank { "无" }}
        避免食材：${preferences.avoids.joinToString("、").ifBlank { "无" }}
        $retryLine
    """.trimIndent()
    return DirectCookPrompt(system, user)
}

private fun DirectAiCookGeneration.filterAvoidingTerms(avoids: List<String>): DirectAiCookGeneration {
    if (avoids.isEmpty()) return this
    return copy(
        recipes = recipes.filterNot { RecipeDatabaseFilter.containsAvoidTerm(it, avoids) },
        mealPlans = mealPlans.filterNot { RecipeDatabaseFilter.containsAvoidTerm(it, avoids) }
    )
}

private fun DirectAiCookGeneration.violatedAvoidTerms(avoids: List<String>): List<String> {
    val terms = RecipeDatabaseFilter.expandedTerms(avoids)
    if (terms.isEmpty()) return emptyList()
    val text = buildString {
        append(summary)
        mealPlans.forEach { plan ->
            append(' ')
            append(plan.title)
            append(' ')
            append(plan.reason)
            append(' ')
            append(plan.dishes.joinToString(" ") { "${it.course} ${it.name} ${it.note}" })
            append(' ')
            append(plan.shoppingList.joinToString(" "))
            append(' ')
            append(plan.timeline.joinToString(" "))
        }
        recipes.forEach { recipe ->
            append(' ')
            append(recipe.name)
            append(' ')
            append(recipe.reason)
            append(' ')
            append(recipe.ingredients.joinToString(" ") { "${it.name} ${it.amount}" })
            append(' ')
            append(recipe.steps.joinToString(" "))
            append(' ')
            append(recipe.tips)
        }
    }.lowercase()
    return terms.filter { term -> text.contains(term.lowercase()) }
}

private fun parseDirectAiCookGeneration(json: Json, content: String): DirectAiCookGeneration? {
    val root = runCatching {
        json.parseToJsonElement(content.extractDirectJsonObject()).jsonObject
    }.getOrNull() ?: return null
    return DirectAiCookGeneration(
        intent = root.text("intent").toDirectRecommendationMode(),
        summary = root.text("summary"),
        mealPlans = root.array("mealPlans").mapNotNull(::parseDirectAiMealPlan),
        recipes = root.array("recipes").mapNotNull(::parseDirectAiRecipe)
    )
}

private fun parseDirectRestaurantKeywordPlan(json: Json, content: String): DirectRestaurantKeywordPlan? {
    val root = runCatching {
        json.parseToJsonElement(content.extractDirectJsonObject()).jsonObject
    }.getOrNull() ?: return null
    return DirectRestaurantKeywordPlan(
        summary = root.text("summary"),
        keywords = root.stringArray("keywords"),
        mustMatch = root.stringArray("mustMatch"),
        preferMatch = root.stringArray("preferMatch"),
        negativeMatch = root.stringArray("negativeMatch"),
        searchStrategy = root.text("searchStrategy").ifBlank { "separate" }
    )
}

private fun parseDirectRestaurantRerank(json: Json, content: String): DirectRestaurantRerankResult? {
    val root = runCatching {
        json.parseToJsonElement(content.extractDirectJsonObject()).jsonObject
    }.getOrNull() ?: return null
    return DirectRestaurantRerankResult(
        rankedIds = root.stringArray("rankedIds"),
        excluded = root.array("excluded").mapNotNull(::parseDirectRestaurantExcluded),
        matchReasons = root.stringListMap("matchReasons"),
        riskWarnings = root.stringListMap("riskWarnings")
    )
}

private fun parseDirectRestaurantExcluded(element: JsonElement): DirectRestaurantExcluded? {
    val obj = element as? JsonObject ?: return null
    val id = obj.text("id")
    if (id.isBlank()) return null
    return DirectRestaurantExcluded(id = id, reason = obj.text("reason"))
}

private fun parseDirectAiMealPlan(element: JsonElement): MealPlanDto? {
    val obj = element as? JsonObject ?: return null
    val title = obj.text("title").ifBlank { obj.text("name") }
    if (title.isBlank()) return null
    val dishes = obj.array("dishes").mapNotNull(::parseDirectAiDish)
    return MealPlanDto(
        id = obj.text("id"),
        title = title,
        structure = obj.text("structure").ifBlank { "AI 生成菜单" },
        cookTime = obj.text("cookTime").ifBlank { obj.text("time").ifBlank { "时间按需" } },
        servings = obj.text("servings").ifBlank { "按需" },
        coverUrl = obj.text("coverUrl"),
        tags = obj.stringArray("tags").ifEmpty { listOf("AI生成") },
        reason = obj.text("reason").ifBlank { obj.text("summary").ifBlank { "按你的需求由 AI 生成。" } },
        dishes = dishes,
        shoppingList = obj.stringArray("shoppingList").ifEmpty { dishes.map { it.name }.distinct() },
        timeline = obj.stringArray("timeline").ifEmpty { listOf("先备菜，再按耗时从长到短烹饪。") }
    )
}

private fun parseDirectAiDish(element: JsonElement): DishItemDto? {
    val obj = element as? JsonObject ?: return null
    val name = obj.text("name")
    if (name.isBlank()) return null
    return DishItemDto(
        course = obj.text("course").ifBlank { "菜品" },
        name = name,
        note = obj.text("note").ifBlank { obj.text("reason") },
        badge = obj.text("badge").ifBlank { "Meat" },
        recipeId = obj.optionalText("recipeId")
    )
}

private fun parseDirectAiRecipe(element: JsonElement): RecipeDto? {
    val obj = element as? JsonObject ?: return null
    val name = obj.text("name")
    if (name.isBlank()) return null
    return RecipeDto(
        id = obj.text("id"),
        name = name,
        cuisine = obj.text("cuisine").ifBlank { obj.text("dish").ifBlank { "AI生成" } },
        taste = obj.stringArray("taste").ifEmpty { listOf("按需") },
        tags = obj.stringArray("tags").ifEmpty { listOf("AI生成") },
        difficulty = obj.text("difficulty").ifBlank { "简单" },
        cookTime = obj.text("cookTime").ifBlank { obj.text("time").ifBlank { "时间按需" } },
        servings = obj.text("servings").ifBlank { "按需" },
        coverUrl = obj.text("coverUrl"),
        reason = obj.text("reason").ifBlank { "按你的需求由 AI 生成。" },
        ingredients = obj.array("ingredients").mapNotNull(::parseDirectAiIngredient),
        steps = obj.stringArray("steps").ifEmpty { obj.stringArray("recipeInstructions") },
        tips = obj.text("tips").ifBlank { "可按个人口味调整盐、辣度和出锅时间。" },
        ratingStars = obj.double("ratingStars") ?: obj.double("rating"),
        source = "ai_generated"
    )
}

private fun parseDirectAiIngredient(element: JsonElement): IngredientDto? {
    val obj = element as? JsonObject
    if (obj != null) {
        val name = obj.text("name").ifBlank { obj.text("ingredient") }
        if (name.isBlank()) return null
        return IngredientDto(name = name, amount = obj.text("amount"))
    }
    val text = element.asText()
    return if (text.isBlank()) null else IngredientDto(name = text, amount = "")
}

private fun String.extractDirectJsonObject(): String {
    val text = trim()
    if (text.startsWith("{") && text.endsWith("}")) return text
    val start = text.indexOf('{')
    val end = text.lastIndexOf('}')
    return if (start >= 0 && end > start) text.substring(start, end + 1) else text
}

private fun String.toDirectRecommendationMode(): RecommendationModeDto {
    val normalized = uppercase()
    return when {
        normalized.contains("COMBO") || normalized.contains("组合") -> RecommendationModeDto.RECIPE_COMBO
        else -> RecommendationModeDto.RECIPE_SINGLE
    }
}

private fun JsonObject.text(key: String): String = this[key].asText()

private fun JsonObject.optionalText(key: String): String? = this[key].asText().takeIf { it.isNotBlank() }

private fun JsonObject.double(key: String): Double? = this[key]?.jsonPrimitive?.doubleOrNull

private fun JsonObject.array(key: String): List<JsonElement> = (this[key] as? JsonArray)?.jsonArray.orEmpty()

private fun JsonObject.stringArray(key: String): List<String> {
    return array(key).flatMap { element ->
        val text = element.asText()
        if (text.isBlank()) {
            emptyList()
        } else {
            text.split(Regex("[,，、/;；\\s]+")).map { it.trim() }.filter { it.isNotBlank() }
        }
    }.distinct()
}

private fun JsonObject.stringListMap(key: String): Map<String, List<String>> {
    val obj = this[key] as? JsonObject ?: return emptyMap()
    return obj.mapValues { (_, value) ->
        when (value) {
            is JsonArray -> value.jsonArray.map { it.asText() }.filter { it.isNotBlank() }
            else -> value.asText().split(Regex("[,，、/;；\\s]+")).map { it.trim() }.filter { it.isNotBlank() }
        }
    }.filterValues { it.isNotEmpty() }
}

private fun JsonElement?.asText(): String = (this as? JsonPrimitive)?.contentOrNull.orEmpty().trim()

private fun String.readableSnippet(maxLength: Int = 180): String {
    return replace(Regex("\\s+"), " ").trim().take(maxLength)
}

@Serializable
private data class DirectChatCompletionRequest(
    val model: String,
    val messages: List<DirectChatMessage>,
    val temperature: Double,
    @SerialName("response_format")
    val responseFormat: DirectResponseFormat
)

@Serializable
private data class DirectPlainChatCompletionRequest(
    val model: String,
    val messages: List<DirectChatMessage>,
    val temperature: Double
)

@Serializable
private data class DirectChatMessage(
    val role: String,
    val content: String
)

@Serializable
private data class DirectResponseFormat(
    val type: String
)

@Serializable
private data class DirectChatCompletionResponse(
    val choices: List<DirectChatChoice> = emptyList()
)

@Serializable
private data class DirectChatChoice(
    val message: DirectChatMessage = DirectChatMessage("assistant", "")
)
