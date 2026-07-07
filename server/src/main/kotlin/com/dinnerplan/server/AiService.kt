package com.dinnerplan.server

import com.dinnerplan.shared.CookRecommendationResponse
import com.dinnerplan.shared.CookSourceDto
import com.dinnerplan.shared.DishItemDto
import com.dinnerplan.shared.IngredientDto
import com.dinnerplan.shared.MealPlanDto
import com.dinnerplan.shared.RecipeDto
import com.dinnerplan.shared.RecommendationModeDto
import com.dinnerplan.shared.UserPreferenceDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Protocol

internal const val AI_CONNECT_TIMEOUT_MILLIS = 20_000L
internal const val AI_REQUEST_TIMEOUT_MILLIS = 150_000L
internal const val AI_SOCKET_TIMEOUT_MILLIS = 150_000L

class AiService(
    private val config: AppConfig,
    private val json: Json
) {
    private val client = HttpClient(OkHttp) {
        install(HttpTimeout) {
            connectTimeoutMillis = AI_CONNECT_TIMEOUT_MILLIS
            requestTimeoutMillis = AI_REQUEST_TIMEOUT_MILLIS
            socketTimeoutMillis = AI_SOCKET_TIMEOUT_MILLIS
        }
        install(ContentNegotiation) {
            json(json)
        }
        engine {
            config {
                retryOnConnectionFailure(true)
                protocols(listOf(Protocol.HTTP_1_1))
            }
        }
    }

    suspend fun parseCookIntent(query: String): AiIntent? {
        return completeIntent(
            system = "你是饮食推荐助手。只输出 JSON，不要输出解释。字段：summary、keywords、taste、avoid、intent，intent 只能是 recipe_single 或 recipe_combo。",
            user = "用户想自己做饭：$query"
        )
    }

    suspend fun parseRestaurantIntent(query: String): AiIntent? {
        return completeIntent(
            system = "你是餐厅搜索需求解析助手。只输出 JSON，不要输出解释。字段：summary、keywords、taste、avoid、intent，intent 只能是 restaurant。",
            user = "用户想找附近餐厅：$query"
        )
    }

    suspend fun parseRestaurantKeywordPlan(query: String): RestaurantKeywordPlan? {
        if (!config.aiConfigured) return null
        val system = """
            你是餐厅搜索关键词解析助手。只输出 JSON，不要解释。
            字段必须是 summary、keywords、mustMatch、preferMatch、negativeMatch、searchStrategy。
            keywords 是要分别用于地图搜索的关键词，不要把所有词拼成一个长关键词。
            searchStrategy 固定输出 separate。
        """.trimIndent()
        val user = "用户附近吃需求：$query"
        return requestRestaurantKeywordPlan(system, user, useJsonMode = true)
            ?: requestRestaurantKeywordPlan(system, user, useJsonMode = false)
    }

    suspend fun rerankRestaurants(
        query: String,
        plan: RestaurantKeywordPlan,
        candidates: List<RestaurantCandidate>,
        limit: Int
    ): RestaurantRerankResult? {
        if (!config.aiConfigured || candidates.isEmpty()) return null
        val system = """
            你是餐厅候选筛选助手。只输出 JSON，不要解释。
            字段必须是 rankedIds、excluded、matchReasons、riskWarnings。
            你只能从候选餐厅 id 中选择，不能新增、不能编造餐厅。
            不能修改评分、距离、地址、人均、名称等原始信息。
            rankedIds 最多返回 $limit 个 id，按匹配度排序。
        """.trimIndent()
        val candidateJson = json.encodeToString(ListSerializer(RestaurantCandidate.serializer()), candidates.take(40))
        val user = """
            用户原始需求：$query
            关键词解析：${json.encodeToString(RestaurantKeywordPlan.serializer(), plan)}
            显示数量：$limit
            候选餐厅：$candidateJson
        """.trimIndent()
        return requestRestaurantRerank(system, user, useJsonMode = true)
            ?: requestRestaurantRerank(system, user, useJsonMode = false)
    }

    suspend fun generateCookRecommendation(
        query: String,
        mode: RecommendationModeDto,
        preferences: UserPreferenceDto
    ): CookRecommendationResponse? {
        if (!config.aiConfigured) return null
        val firstPrompt = AiRecipeGenerator.buildPrompt(query, mode, preferences)
        val firstGeneration = requestCookGeneration(firstPrompt.system, firstPrompt.user, useJsonMode = true)
            ?: requestCookGeneration(firstPrompt.system, firstPrompt.user, useJsonMode = false)
            ?: return null
        val violatedTerms = AiRecipeGenerator.violatedAvoidTerms(firstGeneration, preferences.avoids)
        val generation = if (violatedTerms.isNotEmpty()) {
            val retryPrompt = AiRecipeGenerator.buildPrompt(query, mode, preferences, violatedTerms)
            requestCookGeneration(retryPrompt.system, retryPrompt.user, useJsonMode = true)
                ?: requestCookGeneration(retryPrompt.system, retryPrompt.user, useJsonMode = false)
                ?: firstGeneration
        } else {
            firstGeneration
        }.let { AiRecipeGenerator.filterAvoidingTerms(it, preferences.avoids) }
        val recipes = generation.recipes.map { recipe ->
            recipe.copy(
                id = recipe.id.ifBlank { "ai_recipe_${recipe.name.hashCode().toString().replace("-", "n")}" },
                source = "ai_generated"
            )
        }
        val mealPlans = generation.mealPlans.map { plan ->
            plan.copy(id = plan.id.ifBlank { "ai_meal_${plan.title.hashCode().toString().replace("-", "n")}" })
        }
        if (recipes.isEmpty() && mealPlans.isEmpty()) return null
        return CookRecommendationResponse(
            intent = generation.intent,
            summary = generation.summary.ifBlank { "已根据你的输入生成做饭建议。" },
            mealPlans = mealPlans,
            recipes = recipes,
            source = CookSourceDto.AI_GENERATED,
            totalMatches = mealPlans.size + recipes.size
        )
    }

    private suspend fun completeIntent(system: String, user: String): AiIntent? {
        if (!config.aiConfigured) return null
        return requestIntent(system, user, useJsonMode = true)
            ?: requestIntent(system, user, useJsonMode = false)
    }

    private suspend fun requestIntent(system: String, user: String, useJsonMode: Boolean): AiIntent? {
        return try {
            val response = client.post(chatCompletionsUrl()) {
                bearerAuth(config.aiApiKey)
                contentType(ContentType.Application.Json)
                if (useJsonMode) {
                    setBody(
                        ChatCompletionRequest(
                            model = config.aiModel,
                            messages = chatMessages(system, user),
                            temperature = 0.2,
                            responseFormat = ResponseFormat("json_object")
                        )
                    )
                } else {
                    setBody(
                        PlainChatCompletionRequest(
                            model = config.aiModel,
                            messages = chatMessages(system, user),
                            temperature = 0.2
                        )
                    )
                }
            }.body<ChatCompletionResponse>()

            val content = response.choices.firstOrNull()?.message?.content.orEmpty()
            json.decodeFromString(AiIntent.serializer(), content.extractJsonObject())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            println(
                "AI request failed (jsonMode=$useJsonMode, model=${config.aiModel}): " +
                    "${error::class.simpleName}: ${error.message}"
            )
            null
        }
    }

    private suspend fun requestCookGeneration(system: String, user: String, useJsonMode: Boolean): AiCookGeneration? {
        return try {
            val response = client.post(chatCompletionsUrl()) {
                bearerAuth(config.aiApiKey)
                contentType(ContentType.Application.Json)
                if (useJsonMode) {
                    setBody(
                        ChatCompletionRequest(
                            model = config.aiModel,
                            messages = chatMessages(system, user),
                            temperature = 0.6,
                            responseFormat = ResponseFormat("json_object")
                        )
                    )
                } else {
                    setBody(
                        PlainChatCompletionRequest(
                            model = config.aiModel,
                            messages = chatMessages(system, user),
                            temperature = 0.6
                        )
                    )
                }
            }.body<ChatCompletionResponse>()

            val content = response.choices.firstOrNull()?.message?.content.orEmpty()
            parseAiCookGeneration(json, content)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            println(
                "AI cook generation failed (jsonMode=$useJsonMode, model=${config.aiModel}): " +
                    "${error::class.simpleName}: ${error.message}"
            )
            null
        }
    }

    private suspend fun requestRestaurantKeywordPlan(system: String, user: String, useJsonMode: Boolean): RestaurantKeywordPlan? {
        return try {
            val response = client.post(chatCompletionsUrl()) {
                bearerAuth(config.aiApiKey)
                contentType(ContentType.Application.Json)
                if (useJsonMode) {
                    setBody(
                        ChatCompletionRequest(
                            model = config.aiModel,
                            messages = chatMessages(system, user),
                            temperature = 0.2,
                            responseFormat = ResponseFormat("json_object")
                        )
                    )
                } else {
                    setBody(
                        PlainChatCompletionRequest(
                            model = config.aiModel,
                            messages = chatMessages(system, user),
                            temperature = 0.2
                        )
                    )
                }
            }.body<ChatCompletionResponse>()

            val content = response.choices.firstOrNull()?.message?.content.orEmpty()
            json.decodeFromString(RestaurantKeywordPlan.serializer(), content.extractJsonObject()).normalized()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            println(
                "AI restaurant keyword parse failed (jsonMode=$useJsonMode, model=${config.aiModel}): " +
                    "${error::class.simpleName}: ${error.message}"
            )
            null
        }
    }

    private suspend fun requestRestaurantRerank(system: String, user: String, useJsonMode: Boolean): RestaurantRerankResult? {
        return try {
            val response = client.post(chatCompletionsUrl()) {
                bearerAuth(config.aiApiKey)
                contentType(ContentType.Application.Json)
                if (useJsonMode) {
                    setBody(
                        ChatCompletionRequest(
                            model = config.aiModel,
                            messages = chatMessages(system, user),
                            temperature = 0.2,
                            responseFormat = ResponseFormat("json_object")
                        )
                    )
                } else {
                    setBody(
                        PlainChatCompletionRequest(
                            model = config.aiModel,
                            messages = chatMessages(system, user),
                            temperature = 0.2
                        )
                    )
                }
            }.body<ChatCompletionResponse>()

            val content = response.choices.firstOrNull()?.message?.content.orEmpty()
            json.decodeFromString(RestaurantRerankResult.serializer(), content.extractJsonObject())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            println(
                "AI restaurant rerank failed (jsonMode=$useJsonMode, model=${config.aiModel}): " +
                    "${error::class.simpleName}: ${error.message}"
            )
            null
        }
    }

    private fun chatCompletionsUrl(): String {
        val normalized = config.aiBaseUrl.trimEnd('/')
        return if (normalized.endsWith("/chat/completions")) {
            normalized
        } else {
            "$normalized/chat/completions"
        }
    }

    private fun chatMessages(system: String, user: String): List<ChatMessage> {
        return listOf(
            ChatMessage("system", system),
            ChatMessage("user", user)
        )
    }
}

@Serializable
data class AiIntent(
    val summary: String = "",
    @Serializable(with = FlexibleStringListSerializer::class)
    val keywords: List<String> = emptyList(),
    @Serializable(with = FlexibleStringListSerializer::class)
    val taste: List<String> = emptyList(),
    @Serializable(with = FlexibleStringListSerializer::class)
    val avoid: List<String> = emptyList(),
    val intent: String = ""
)

@Serializable
internal data class AiCookGeneration(
    val intent: RecommendationModeDto = RecommendationModeDto.RECIPE_SINGLE,
    val summary: String = "",
    val mealPlans: List<MealPlanDto> = emptyList(),
    val recipes: List<RecipeDto> = emptyList()
)

internal fun parseAiCookGeneration(json: Json, content: String): AiCookGeneration? {
    val root = runCatching {
        json.parseToJsonElement(content.extractJsonObject()).jsonObject
    }.getOrNull() ?: return null
    return AiCookGeneration(
        intent = root.text("intent").toRecommendationMode(),
        summary = root.text("summary"),
        mealPlans = root.array("mealPlans").mapNotNull(::parseAiMealPlan),
        recipes = root.array("recipes").mapNotNull(::parseAiRecipe)
    )
}

internal fun String.extractJsonObject(): String {
    val text = trim()
    if (text.startsWith("{") && text.endsWith("}")) return text
    val start = text.indexOf('{')
    val end = text.lastIndexOf('}')
    return if (start >= 0 && end > start) text.substring(start, end + 1) else text
}

private fun parseAiMealPlan(element: JsonElement): MealPlanDto? {
    val obj = element.asObject() ?: return null
    val title = obj.text("title").ifBlank { obj.text("name") }
    if (title.isBlank()) return null
    val dishes = obj.array("dishes").mapNotNull(::parseAiDish)
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
        shoppingList = obj.stringArray("shoppingList").ifEmpty {
            dishes.map { it.name }.distinct()
        },
        timeline = obj.stringArray("timeline").ifEmpty { listOf("先备菜，再按耗时从长到短烹饪。") }
    )
}

private fun parseAiDish(element: JsonElement): DishItemDto? {
    val obj = element.asObject() ?: return null
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

private fun parseAiRecipe(element: JsonElement): RecipeDto? {
    val obj = element.asObject() ?: return null
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
        ingredients = obj.array("ingredients").mapNotNull(::parseAiIngredient),
        steps = obj.stringArray("steps").ifEmpty { obj.stringArray("recipeInstructions") },
        tips = obj.text("tips").ifBlank { "可按个人口味调整盐、辣度和出锅时间。" },
        ratingStars = obj.double("ratingStars") ?: obj.double("rating"),
        source = "ai_generated"
    )
}

private fun parseAiIngredient(element: JsonElement): IngredientDto? {
    val obj = element.asObject()
    if (obj != null) {
        val name = obj.text("name").ifBlank { obj.text("ingredient") }
        if (name.isBlank()) return null
        return IngredientDto(name = name, amount = obj.text("amount"))
    }
    val text = element.asText()
    return if (text.isBlank()) null else IngredientDto(name = text, amount = "")
}

private fun String.toRecommendationMode(): RecommendationModeDto {
    val normalized = uppercase()
    return when {
        normalized.contains("COMBO") || normalized.contains("组合") -> RecommendationModeDto.RECIPE_COMBO
        else -> RecommendationModeDto.RECIPE_SINGLE
    }
}

private fun JsonObject.text(key: String): String {
    return this[key].asText()
}

private fun JsonObject.optionalText(key: String): String? {
    return this[key].asText().takeIf { it.isNotBlank() }
}

private fun JsonObject.double(key: String): Double? {
    return this[key]?.jsonPrimitive?.doubleOrNull
}

private fun JsonObject.array(key: String): List<JsonElement> {
    return (this[key] as? JsonArray)?.jsonArray.orEmpty()
}

private fun JsonObject.stringArray(key: String): List<String> {
    return array(key).mapNotNull { it.asText().takeIf(String::isNotBlank) }
}

private fun JsonElement?.asText(): String {
    return (this as? JsonPrimitive)?.contentOrNull.orEmpty().trim()
}

private fun JsonElement.asObject(): JsonObject? {
    return this as? JsonObject
}

private object FlexibleStringListSerializer : KSerializer<List<String>> {
    private val delegate = ListSerializer(String.serializer())
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): List<String> {
        val jsonDecoder = decoder as? JsonDecoder ?: return delegate.deserialize(decoder)
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonArray -> element.flatMap { item ->
                item.jsonPrimitive.contentOrNull?.let(::splitValues).orEmpty()
            }
            is JsonPrimitive -> element.contentOrNull?.let(::splitValues).orEmpty()
            else -> emptyList()
        }.distinct()
    }

    override fun serialize(encoder: Encoder, value: List<String>) {
        delegate.serialize(encoder, value)
    }

    private fun splitValues(value: String): List<String> {
        return value
            .split(Regex("[,，、/;；\\s]+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }
}

@Serializable
private data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double,
    @kotlinx.serialization.SerialName("response_format")
    val responseFormat: ResponseFormat
)

@Serializable
private data class PlainChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double
)

@Serializable
private data class ChatMessage(
    val role: String,
    val content: String
)

@Serializable
private data class ResponseFormat(
    val type: String
)

@Serializable
private data class ChatCompletionResponse(
    val choices: List<ChatChoice> = emptyList()
)

@Serializable
private data class ChatChoice(
    val message: ChatMessage = ChatMessage("assistant", "")
)
