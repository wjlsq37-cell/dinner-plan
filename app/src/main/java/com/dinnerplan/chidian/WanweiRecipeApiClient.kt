package com.dinnerplan.chidian

import com.dinnerplan.shared.IngredientDto
import com.dinnerplan.shared.RecipeDto
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.Protocol
import java.net.URLEncoder
import kotlin.math.round

internal data class WanweiRecipeSearchResult(
    val recipes: List<RecipeDto>,
    val totalMatches: Int,
    val fallbackReason: String? = null
)

internal data class WanweiRecipeItem(
    val id: String = "",
    val cpName: String = "",
    val des: String = "",
    val type: String = "",
    val typeV1: String = "",
    val typeV2: String = "",
    val typeV3: String = "",
    val largeImg: String = "",
    val smallImg: String = "",
    val yl: List<WanweiIngredient> = emptyList(),
    val steps: List<WanweiStep> = emptyList(),
    val difficulty: String = "",
    val duration: String = "",
    val tip: String = ""
)

internal data class WanweiIngredient(
    val ylName: String = "",
    val ylUnit: String = ""
)

internal data class WanweiStep(
    val orderNum: String = "",
    val content: String = "",
    val imgUrl: String = ""
)

internal class WanweiRecipeApiClient(
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val client = HttpClient(OkHttp) {
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
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

    suspend fun searchRecipes(
        settings: DeveloperSettings,
        query: String,
        page: Int = 1
    ): WanweiRecipeSearchResult {
        return when (settings.selectedRecipeApiSource) {
            RecipeApiSource.Wanwei -> searchWanweiRecipes(settings, query, page)
            RecipeApiSource.Mxnzp -> searchMxnzpRecipes(settings, query, page)
            RecipeApiSource.Custom -> searchCustomRecipes(settings, query, page)
        }
    }

    suspend fun loadMxnzpRecipeDetail(settings: DeveloperSettings, recipe: RecipeDto): RecipeDto? {
        if (!isMxnzpRecipe(recipe)) return null
        val appId = settings.recipeApiAppId
        val appSecret = settings.recipeApiSecret
        if (appId.isBlank() || appSecret.isBlank()) return null
        return try {
            withTimeout(settings.maxWaitMillis) {
                requestMxnzpRecipeDetail(mxnzpRawId(recipe.id), appId, appSecret)
                    .mergeMxnzpDetailInto(recipe)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            logInternalIssue("mxnzp recipe detail load failed", error.message, error)
            null
        }
    }

    private suspend fun searchWanweiRecipes(
        settings: DeveloperSettings,
        query: String,
        page: Int
    ): WanweiRecipeSearchResult {
        val appKey = settings.wanweiRecipeAppKey.trim()
        if (appKey.isBlank()) {
            logInternalIssue("Wanwei recipe configuration missing", "appKey is blank")
            return WanweiRecipeSearchResult(
                recipes = emptyList(),
                totalMatches = 0,
                fallbackReason = friendlyStatusMessage("万维易源 AppKey 未填写", UserMessageContext.Config)
            )
        }

        return try {
            withTimeout(settings.maxWaitMillis) {
                val keywordRecipes = requestWanweiRecipes(
                    baseUrl = settings.effectiveRecipeApiUrl,
                    path = "1164-4",
                    appKey = appKey,
                    form = mapOf(
                        "keyword" to query,
                        "keyWord" to query,
                        "word" to query,
                        "cpName" to query,
                        "name" to query,
                        "maxResults" to settings.safePageSize.toString(),
                        "page" to page.toString()
                    )
                )

                val category = inferWanweiCategory(query)
                val categoryRecipes = if ((category != null || keywordRecipes.isEmpty()) && keywordRecipes.size < settings.safePageSize) {
                    requestWanweiRecipes(
                        baseUrl = settings.effectiveRecipeApiUrl,
                        path = "1164-1",
                        appKey = appKey,
                        form = mapOf(
                            "type" to (category ?: "家常菜"),
                            "cpName" to query,
                            "maxResults" to settings.safePageSize.toString(),
                            "page" to page.toString()
                        )
                    )
                } else {
                    emptyList()
                }

                val recipes = WanweiRecipeMapper.rankRecipes(
                    (keywordRecipes + categoryRecipes)
                        .distinctBy { it.id }
                        .take(settings.safePageSize * 2),
                    query
                ).take(settings.safePageSize)

                WanweiRecipeSearchResult(
                    recipes = recipes,
                    totalMatches = recipes.size,
                    fallbackReason = if (recipes.isEmpty()) friendlyStatusMessage("暂未找到", UserMessageContext.SearchEmpty) else null
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            logInternalIssue("Wanwei recipe search failed", error.message, error)
            WanweiRecipeSearchResult(
                recipes = emptyList(),
                totalMatches = 0,
                fallbackReason = recipeApiErrorMessage("万维易源", error)
            )
        }
    }

    private suspend fun searchMxnzpRecipes(
        settings: DeveloperSettings,
        query: String,
        page: Int
    ): WanweiRecipeSearchResult {
        if (settings.recipeApiAppId.isBlank() || settings.recipeApiSecret.isBlank()) {
            logInternalIssue("mxnzp recipe configuration missing", "app_id/app_secret is blank")
            return WanweiRecipeSearchResult(
                recipes = emptyList(),
                totalMatches = 0,
                fallbackReason = friendlyStatusMessage("mxnzp app_id 或 app_secret 未填写", UserMessageContext.Config)
            )
        }
        val appId = settings.recipeApiAppId
        val appSecret = settings.recipeApiSecret
        return try {
            withTimeout(settings.maxWaitMillis) {
                val response = client.get(settings.effectiveRecipeApiUrl) {
                    parameter("keyword", query)
                    parameter("page", page)
                    parameter("app_id", appId.trim())
                    parameter("app_secret", appSecret.trim())
                }
                val text = response.bodyAsText()
                if (!response.status.isSuccess()) {
                    throw IllegalStateException("mxnzp HTTP ${response.status.value}：${text.take(120)}")
                }
                val recipes = parseMxnzpResponse(text)
                    .let { WanweiRecipeMapper.rankRecipes(it, query) }
                    .take(settings.safePageSize)
                WanweiRecipeSearchResult(
                    recipes = recipes,
                    totalMatches = recipes.size,
                    fallbackReason = if (recipes.isEmpty()) friendlyStatusMessage("暂未找到", UserMessageContext.SearchEmpty) else null
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            logInternalIssue("mxnzp recipe search failed", error.message, error)
            WanweiRecipeSearchResult(
                recipes = emptyList(),
                totalMatches = 0,
                fallbackReason = recipeApiErrorMessage("mxnzp", error)
            )
        }
    }

    private suspend fun searchCustomRecipes(
        settings: DeveloperSettings,
        query: String,
        page: Int
    ): WanweiRecipeSearchResult {
        val endpoint = settings.effectiveRecipeApiUrl
        if (endpoint.isBlank()) {
            logInternalIssue("Custom recipe API configuration missing", "recipe API URL is blank")
            return WanweiRecipeSearchResult(
                recipes = emptyList(),
                totalMatches = 0,
                fallbackReason = friendlyStatusMessage("自定义菜谱 API 地址未填写", UserMessageContext.Config)
            )
        }
        return searchGenericGetRecipes(
            sourceName = "自定义菜谱 API",
            idPrefix = "custom_recipe",
            endpoint = endpoint,
            query = query,
            page = page,
            pageSize = settings.safePageSize,
            appId = settings.recipeApiAppId,
            appSecret = settings.recipeApiSecret,
            maxWaitMillis = settings.maxWaitMillis
        )
    }

    private suspend fun searchGenericGetRecipes(
        sourceName: String,
        idPrefix: String,
        endpoint: String,
        query: String,
        page: Int,
        pageSize: Int,
        appId: String,
        appSecret: String,
        maxWaitMillis: Long
    ): WanweiRecipeSearchResult {
        return try {
            withTimeout(maxWaitMillis) {
                val response = client.get(endpoint) {
                    parameter("keyword", query)
                    parameter("keyWord", query)
                    parameter("word", query)
                    parameter("name", query)
                    parameter("page", page)
                    parameter("num", pageSize)
                    parameter("size", pageSize)
                    parameter("limit", pageSize)
                    if (appId.isNotBlank()) parameter("app_id", appId.trim())
                    if (appSecret.isNotBlank()) parameter("app_secret", appSecret.trim())
                }
                val text = response.bodyAsText()
                if (!response.status.isSuccess()) {
                    throw IllegalStateException("$sourceName HTTP ${response.status.value}：${text.take(120)}")
                }
                val recipes = parseGenericRecipeResponse(
                    text = text,
                    sourceName = sourceName,
                    idPrefix = idPrefix,
                    defaultTag = sourceName
                )
                    .let { WanweiRecipeMapper.rankRecipes(it, query) }
                    .take(pageSize)
                WanweiRecipeSearchResult(
                    recipes = recipes,
                    totalMatches = recipes.size,
                    fallbackReason = if (recipes.isEmpty()) friendlyStatusMessage("暂未找到", UserMessageContext.SearchEmpty) else null
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            logInternalIssue("$sourceName recipe search failed", error.message, error)
            WanweiRecipeSearchResult(
                recipes = emptyList(),
                totalMatches = 0,
                fallbackReason = recipeApiErrorMessage(sourceName, error)
            )
        }
    }

    private suspend fun requestWanweiRecipes(
        baseUrl: String,
        path: String,
        appKey: String,
        form: Map<String, String>
    ): List<RecipeDto> {
        val response = client.post("${baseUrl.trimEnd('/')}/$path?appKey=${urlEncode(appKey)}") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(encodeForm(form.filterValues { it.isNotBlank() }))
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw IllegalStateException("万维易源 HTTP ${response.status.value}：${text.take(120)}")
        }
        return parseWanweiResponse(text)
            .map(WanweiRecipeMapper::toRecipeDto)
    }

    internal fun parseWanweiResponse(text: String): List<WanweiRecipeItem> {
        val root = json.parseToJsonElement(text).jsonObject
        val code = root.int("showapi_res_code")
        if (code != null && code != 0) {
            throw IllegalStateException(showApiError(code, root.string("showapi_res_error")))
        }
        val body = root.obj("showapi_res_body") ?: root
        val retCode = body.int("ret_code")
        if (retCode != null && retCode != 0) {
            throw IllegalStateException(showApiError(retCode, body.string("msg").ifBlank { body.string("remark") }))
        }

        return recipeObjects(body)
            .mapNotNull(::toWanweiRecipeItem)
            .distinctBy { it.id.ifBlank { it.cpName } }
    }

    private suspend fun requestMxnzpRecipeDetail(id: String, appId: String, appSecret: String): RecipeDto {
        val response = client.get(MXNZP_RECIPE_DETAIL_URL) {
            parameter("id", id)
            parameter("app_id", appId.trim())
            parameter("app_secret", appSecret.trim())
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw IllegalStateException("mxnzp detail HTTP ${response.status.value}：${text.take(120)}")
        }
        return parseMxnzpDetailResponse(text)
    }

    internal fun parseMxnzpResponse(text: String): List<RecipeDto> {
        return parseGenericRecipeResponse(
            text = text,
            sourceName = "mxnzp",
            idPrefix = "mxnzp",
            defaultTag = "mxnzp"
        )
    }

    internal fun parseMxnzpDetailResponse(text: String): RecipeDto {
        val root = json.parseToJsonElement(text).jsonObject
        val code = root.int("code") ?: root.int("status")
        val success = code == null || code == 0 || code == 1 || code == 200
        if (!success) {
            throw IllegalStateException("mxnzp 详情接口返回异常（$code）：${root.string("msg").ifBlank { root.string("message") }}")
        }
        val body = root.obj("data") ?: root.obj("result") ?: root
        val item = toWanweiRecipeItem(body) ?: throw IllegalStateException("mxnzp 详情缺少菜谱名称")
        return WanweiRecipeMapper.toRecipeDto(
            item = item,
            sourceName = "mxnzp",
            idPrefix = "mxnzp",
            defaultTag = "mxnzp"
        )
    }

    private fun parseGenericRecipeResponse(
        text: String,
        sourceName: String,
        idPrefix: String,
        defaultTag: String
    ): List<RecipeDto> {
        val root = json.parseToJsonElement(text).jsonObject
        val code = root.int("code") ?: root.int("status") ?: root.int("showapi_res_code")
        val success = code == null || code == 0 || code == 1 || code == 200
        if (!success) {
            throw IllegalStateException("$sourceName 接口返回异常（$code）：${root.string("msg").ifBlank { root.string("message") }}")
        }
        val body = root.obj("data")
            ?: root.obj("result")
            ?: root.obj("showapi_res_body")
            ?: root
        return recipeObjects(body)
            .mapNotNull(::toWanweiRecipeItem)
            .distinctBy { it.id.ifBlank { it.cpName } }
            .map { item ->
                WanweiRecipeMapper.toRecipeDto(
                    item = item,
                    sourceName = sourceName,
                    idPrefix = idPrefix,
                    defaultTag = defaultTag
                )
            }
    }

    private fun recipeObjects(root: JsonObject): List<JsonObject> {
        val direct = listOf(
            "contentlist",
            "contentList",
            "list",
            "data",
            "result",
            "datas",
            "recipes",
            "recipeList",
            "items",
            "rows",
            "records",
            "menu",
            "menus",
            "cookbooks",
            "cookbookList"
        )
            .flatMap { key -> root.array(key).mapNotNull { it as? JsonObject } }
        val pageBean = (root.obj("pagebean") ?: root.obj("pageBean"))?.let(::recipeObjects).orEmpty()
        val nested = root.values
            .filterIsInstance<JsonObject>()
            .filterNot { it === root.obj("pagebean") || it === root.obj("pageBean") }
            .flatMap(::recipeObjects)
        val self = if (
            root.string("cpName").isNotBlank() ||
            root.string("name").isNotBlank() ||
            root.string("title").isNotBlank()
        ) {
            listOf(root)
        } else {
            emptyList()
        }
        return (self + direct + pageBean + nested).distinct()
    }

    private fun toWanweiRecipeItem(obj: JsonObject): WanweiRecipeItem? {
        val name = obj.string("cpName")
            .ifBlank { obj.string("name") }
            .ifBlank { obj.string("title") }
            .ifBlank { obj.string("menuName") }
            .ifBlank { obj.string("recipeName") }
            .ifBlank { obj.string("foodName") }
        if (name.isBlank()) return null
        return WanweiRecipeItem(
            id = obj.string("id")
                .ifBlank { obj.string("_id") }
                .ifBlank { obj.string("cpId") }
                .ifBlank { obj.string("recipeId") }
                .ifBlank { obj.string("menuId") },
            cpName = name,
            des = obj.string("des")
                .ifBlank { obj.string("description") }
                .ifBlank { obj.string("desc") }
                .ifBlank { obj.string("summary") }
                .ifBlank { obj.string("intro") },
            type = obj.string("type").ifBlank { obj.string("category") },
            typeV1 = obj.string("type_v1").ifBlank { obj.string("typeV1") }.ifBlank { obj.string("categoryName") },
            typeV2 = obj.string("type_v2").ifBlank { obj.string("typeV2") },
            typeV3 = obj.string("type_v3").ifBlank { obj.string("typeV3") },
            largeImg = obj.string("largeImg")
                .ifBlank { obj.string("large_img") }
                .ifBlank { obj.string("imgUrl") }
                .ifBlank { obj.string("pic") }
                .ifBlank { obj.string("img") }
                .ifBlank { obj.string("image") }
                .ifBlank { obj.string("cover") }
                .ifBlank { obj.string("coverUrl") },
            smallImg = obj.string("smallImg")
                .ifBlank { obj.string("small_img") }
                .ifBlank { obj.string("thumbnail") }
                .ifBlank { obj.string("smallPic") },
            yl = obj.array("yl").mapNotNull(::toWanweiIngredient)
                .ifEmpty { obj.array("recipeIngredient").mapNotNull(::toWanweiIngredient) }
                .ifEmpty { obj.array("ingredients").mapNotNull(::toWanweiIngredient) }
                .ifEmpty { obj.array("materials").mapNotNull(::toWanweiIngredient) }
                .ifEmpty { obj.array("material").mapNotNull(::toWanweiIngredient) }
                .ifEmpty { obj.array("ingredient").mapNotNull(::toWanweiIngredient) },
            steps = obj.array("steps").mapNotNull(::toWanweiStep)
                .ifEmpty { obj.array("recipeInstructions").mapNotNull(::toWanweiStep) }
                .ifEmpty { obj.array("instruction").mapNotNull(::toWanweiStep) }
                .ifEmpty { obj.array("method").mapNotNull(::toWanweiStep) }
                .ifEmpty { obj.array("process").mapNotNull(::toWanweiStep) }
                .ifEmpty { obj.array("practice").mapNotNull(::toWanweiStep) }
                .ifEmpty { obj.array("cookingSteps").mapNotNull(::toWanweiStep) },
            difficulty = obj.string("difficulty"),
            duration = obj.string("duration").ifBlank { obj.string("cookTime") }.ifBlank { obj.string("time") },
            tip = obj.string("tip").ifBlank { obj.string("tips") }.ifBlank { obj.string("notice") }
        )
    }

    private fun toWanweiIngredient(element: JsonElement): WanweiIngredient? {
        val obj = element as? JsonObject
        if (obj != null) {
            val name = obj.string("ylName")
                .ifBlank { obj.string("yl_name") }
                .ifBlank { obj.string("name") }
                .ifBlank { obj.string("materialName") }
                .ifBlank { obj.string("food") }
                .ifBlank { obj.string("ingredient") }
            if (name.isBlank()) return null
            return WanweiIngredient(
                name,
                obj.string("ylUnit")
                    .ifBlank { obj.string("yl_unit") }
                    .ifBlank { obj.string("amount") }
                    .ifBlank { obj.string("unit") }
                    .ifBlank { obj.string("weight") }
            )
        }
        val text = element.asText()
        return if (text.isBlank()) null else WanweiIngredient(text, "")
    }

    private fun toWanweiStep(element: JsonElement): WanweiStep? {
        val obj = element as? JsonObject
        if (obj != null) {
            val content = obj.string("content")
                .ifBlank { obj.string("text") }
                .ifBlank { obj.string("step") }
                .ifBlank { obj.string("title") }
                .ifBlank { obj.string("desc") }
                .ifBlank { obj.string("description") }
                .ifBlank { obj.string("detail") }
            if (content.isBlank()) return null
            return WanweiStep(
                orderNum = obj.string("orderNum")
                    .ifBlank { obj.string("order_num") }
                    .ifBlank { obj.string("order") }
                    .ifBlank { obj.string("stepNum") }
                    .ifBlank { obj.string("stepIndex") }
                    .ifBlank { obj.string("step") }
                    .ifBlank { obj.string("no") },
                content = content,
                imgUrl = obj.string("imgUrl")
                    .ifBlank { obj.string("img_url") }
                    .ifBlank { obj.string("url") }
                    .ifBlank { obj.string("img") }
                    .ifBlank { obj.string("pic") }
                    .ifBlank { obj.string("image") }
            )
        }
        val text = element.asText()
        return if (text.isBlank()) null else WanweiStep(content = text)
    }
}

private fun mxnzpRawId(id: String): String {
    return id.removePrefix("mxnzp_")
}

private fun isMxnzpRecipe(recipe: RecipeDto): Boolean {
    return recipe.source.equals("mxnzp", ignoreCase = true) || recipe.id.startsWith("mxnzp_")
}

private fun RecipeDto.mergeMxnzpDetailInto(searchRecipe: RecipeDto): RecipeDto {
    return searchRecipe.copy(
        cuisine = cuisine.ifBlank { searchRecipe.cuisine },
        taste = taste.ifEmpty { searchRecipe.taste },
        tags = tags.ifEmpty { searchRecipe.tags },
        difficulty = difficulty.ifBlank { searchRecipe.difficulty },
        cookTime = cookTime.ifBlank { searchRecipe.cookTime },
        servings = servings.ifBlank { searchRecipe.servings },
        coverUrl = searchRecipe.coverUrl.ifBlank { coverUrl },
        reason = searchRecipe.reason.ifBlank { reason },
        ingredients = ingredients.ifEmpty { searchRecipe.ingredients },
        steps = steps.ifEmpty { searchRecipe.steps },
        tips = tips.ifBlank { searchRecipe.tips },
        ratingStars = ratingStars ?: searchRecipe.ratingStars,
        stepImageUrls = stepImageUrls.ifEmpty { searchRecipe.stepImageUrls }
    )
}

internal object WanweiRecipeMapper {
    fun toRecipeDto(
        item: WanweiRecipeItem,
        sourceName: String = "万维易源",
        idPrefix: String = "showapi",
        defaultTag: String = sourceName
    ): RecipeDto {
        val tags = listOf(item.type, item.typeV1, item.typeV2, item.typeV3)
            .map { it.trim() }
            .filter { it.isNotBlank() && it != "Unknown" }
            .distinct()
        val sortedStepItems = item.steps
            .sortedBy { stepOrder(it.orderNum) }
            .filter { it.content.isNotBlank() }
        val sortedSteps = sortedStepItems.map { it.content.trim() }
        val cover = item.largeImg.ifBlank {
            item.smallImg.ifBlank {
                item.steps.firstOrNull { it.imgUrl.isNotBlank() }?.imgUrl.orEmpty()
            }
        }
        val stepImageUrls = if (sortedStepItems.any { it.imgUrl.isNotBlank() }) {
            sortedStepItems.map { it.imgUrl.trim() }
        } else {
            emptyList()
        }
        val name = item.cpName.trim()
        return RecipeDto(
            id = "${idPrefix}_${item.id.ifBlank { stableRecipeId(name) }}",
            name = name,
            cuisine = tags.firstOrNull().orEmpty().ifBlank { "$sourceName 菜谱" },
            taste = tags.take(3).ifEmpty { listOf("家常") },
            tags = tags.ifEmpty { listOf(defaultTag) },
            difficulty = item.difficulty.ifBlank { inferDifficulty(sortedSteps.size) },
            cookTime = item.duration.ifBlank { inferCookTime(sortedSteps.size) },
            servings = "按需",
            coverUrl = cover,
            reason = item.des.ifBlank { "来自${sourceName}菜谱库，按你的搜索词匹配。" },
            ingredients = item.yl
                .mapNotNull { ingredient ->
                    val namePart = ingredient.ylName.trim()
                    if (namePart.isBlank()) null else IngredientDto(namePart, ingredient.ylUnit.trim())
                },
            steps = sortedSteps.ifEmpty { listOf("根据食材准备并按家常做法烹饪。") },
            tips = item.tip.ifBlank { "可按个人口味调整调味和火候。" },
            ratingStars = qualityRating(item),
            source = sourceName,
            stepImageUrls = stepImageUrls
        )
    }

    fun rankRecipes(recipes: List<RecipeDto>, query: String): List<RecipeDto> {
        return recipes.sortedWith(
            compareByDescending<RecipeDto> { it.ratingStars ?: 0.0 }
                .thenByDescending { relevanceScore(it, query) }
                .thenBy { it.name.length }
        )
    }

    private fun qualityRating(item: WanweiRecipeItem): Double {
        var score = 1.6
        if (item.cpName.isNotBlank()) score += 0.5
        if (item.des.length >= 8) score += 0.45
        if (item.largeImg.isNotBlank() || item.smallImg.isNotBlank() || item.steps.any { it.imgUrl.isNotBlank() }) score += 0.35
        score += when {
            item.yl.size >= 5 -> 0.85
            item.yl.size >= 3 -> 0.65
            item.yl.size >= 1 -> 0.25
            else -> 0.0
        }
        score += when {
            item.steps.size >= 5 -> 1.05
            item.steps.size >= 3 -> 0.85
            item.steps.size >= 1 -> 0.25
            else -> 0.0
        }
        if (listOf(item.type, item.typeV1, item.typeV2, item.typeV3).any { it.isNotBlank() }) score += 0.35
        if (item.tip.length >= 4) score += 0.2
        return (round(score.coerceIn(1.0, 5.0) * 10) / 10.0)
    }

    private fun relevanceScore(recipe: RecipeDto, query: String): Int {
        val normalizedQuery = query.trim()
        val text = "${recipe.name} ${recipe.cuisine} ${recipe.tags.joinToString(" ")} ${recipe.ingredients.joinToString(" ") { it.name }}"
        return when {
            normalizedQuery.isBlank() -> 0
            recipe.name.contains(normalizedQuery) -> 100
            normalizedQuery.contains(recipe.name) -> 80
            else -> normalizedQuery.split(Regex("[,，、\\s]+"))
                .filter { it.isNotBlank() }
                .fold(0) { score, term -> score + if (text.contains(term)) 10 else 0 }
        }
    }

    private fun inferDifficulty(stepCount: Int): String = when {
        stepCount <= 2 -> "简单"
        stepCount <= 5 -> "中等"
        else -> "进阶"
    }

    private fun inferCookTime(stepCount: Int): String = when {
        stepCount <= 2 -> "约20分钟"
        stepCount <= 5 -> "约35分钟"
        else -> "约50分钟"
    }

    private fun stepOrder(value: String): Int {
        return Regex("\\d+").find(value)?.value?.toIntOrNull() ?: Int.MAX_VALUE
    }

    private fun stableRecipeId(name: String): String = name.hashCode().toString().replace("-", "n")
}

private fun inferWanweiCategory(query: String): String? {
    return when {
        query.contains("鸡蛋") || query.contains("鸭蛋") || query.contains("蛋") -> "蛋类"
        query.contains("牛肉") || query.contains("猪肉") || query.contains("排骨") || query.contains("羊肉") -> "肉类"
        query.contains("鸡") || query.contains("鸭") || query.contains("鹅") -> "禽类"
        query.contains("鱼") || query.contains("虾") || query.contains("海鲜") -> "水产"
        query.contains("汤") || query.contains("羹") -> "汤羹"
        query.contains("素") || query.contains("青菜") || query.contains("蔬菜") -> "蔬菜"
        query.contains("主食") || query.contains("饭") || query.contains("面") || query.contains("饼") -> "主食"
        else -> null
    }
}

private fun showApiError(code: Int, message: String): String {
    val detail = message.ifBlank { "未知错误" }
    return when {
        detail.contains("appKey", ignoreCase = true) || detail.contains("授权") || detail.contains("签名") ->
            "万维易源 AppKey 不正确或未授权：$detail"
        detail.contains("未订购") || detail.contains("订购") ->
            "万维易源菜谱服务未订购或未开通：$detail"
        detail.contains("余额") || detail.contains("次数") || detail.contains("欠费") ->
            "万维易源调用次数或余额不足：$detail"
        detail.contains("频率") || detail.contains("limit", ignoreCase = true) ->
            "万维易源请求过于频繁，请稍后重试：$detail"
        else -> "万维易源接口返回异常（$code）：$detail"
    }
}

private fun recipeApiErrorMessage(sourceName: String, error: Throwable): String {
    val message = error.message.orEmpty()
    return friendlyStatusMessage(message.ifBlank { "$sourceName 菜谱 API 请求失败" }, UserMessageContext.Recipe)
}

private fun JsonObject.string(key: String): String = this[key].asText()

private fun JsonObject.int(key: String): Int? = string(key).toIntOrNull()

private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

private fun JsonObject.array(key: String): List<JsonElement> = (this[key] as? JsonArray)?.jsonArray.orEmpty()

private fun JsonElement?.asText(): String = (this as? JsonPrimitive)?.contentOrNull.orEmpty().trim()

private fun encodeForm(values: Map<String, String>): String {
    return values.entries.joinToString("&") { (key, value) -> "${urlEncode(key)}=${urlEncode(value)}" }
}

private fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8")
