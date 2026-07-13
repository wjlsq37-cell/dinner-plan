package com.dinnerplan.chidian

import com.dinnerplan.shared.LocationDto
import com.dinnerplan.shared.RestaurantDto
import com.dinnerplan.shared.RestaurantRecommendationResponse
import com.dinnerplan.shared.RestaurantSearchSpec
import com.dinnerplan.shared.UserPreferenceDto
import com.dinnerplan.shared.restaurantRelevanceScore
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import okhttp3.Protocol

internal class DirectAmapApiClient(
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val directAiApiClient: DirectAiApiClient = DirectAiApiClient()
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

    suspend fun searchRestaurants(
        settings: DeveloperSettings,
        query: String,
        locationText: String,
        location: LocationDto,
        preferences: UserPreferenceDto,
        broadSearch: Boolean = false
    ): RestaurantRecommendationResponse {
        if (settings.amapWebKey.isBlank()) {
            logInternalIssue("Direct Amap configuration missing", "amapWebKey is blank")
            return RestaurantRecommendationResponse(
                restaurants = emptyList(),
                locationUsed = LocationDto(text = locationText),
                fallbackReason = friendlyStatusMessage("高德 Web Key 未填写", UserMessageContext.Config)
            )
        }
        return try {
            withTimeout(settings.maxWaitMillis) {
                val resolvedLocation = resolveLocation(settings, location)
                    ?: return@withTimeout RestaurantRecommendationResponse(
                        restaurants = emptyList(),
                        locationUsed = location,
                        fallbackReason = friendlyStatusMessage("没有可用位置", UserMessageContext.Location)
                    )

                val limit = preferences.restaurantResultLimit.coerceIn(1, 50)
                val keywordPlan = if (broadSearch) {
                    broadDirectRestaurantKeywordPlan()
                } else {
                    directAiApiClient.parseRestaurantKeywordPlan(settings, query)
                        ?: DirectRestaurantKeywordAiParser.fallbackPlan(query)
                }
                val candidates = DirectAmapMultiKeywordSearch { keyword ->
                    fetchAroundCandidates(
                        settings = settings,
                        keyword = keyword,
                        location = resolvedLocation,
                        radiusMeters = preferences.defaultDistanceKm * 1000
                    )
                }.search(keywordPlan)

                if (candidates.isEmpty()) {
                    return@withTimeout RestaurantRecommendationResponse(
                        restaurants = emptyList(),
                        locationUsed = resolvedLocation,
                        fallbackReason = friendlyStatusMessage("暂未找到", UserMessageContext.SearchEmpty)
                    )
                }

                val rerank = DirectRestaurantAiReranker.fallbackRerank(query, keywordPlan, candidates, limit)
                val restaurants = DirectRestaurantAiReranker.toRestaurants(candidates, rerank, limit)
                RestaurantRecommendationResponse(
                    restaurants = restaurants,
                    locationUsed = resolvedLocation,
                    fallbackReason = null
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            logInternalIssue("Direct Amap request failed", error.message, error)
            RestaurantRecommendationResponse(
                restaurants = emptyList(),
                locationUsed = LocationDto(text = locationText),
                fallbackReason = amapErrorMessage(error)
            )
        }
    }

    private suspend fun resolveLocation(settings: DeveloperSettings, location: LocationDto): LocationDto? {
        if (location.latitude != null && location.longitude != null) return location
        val address = location.text.orEmpty().ifBlank { return null }
        val root = getAmapJson("https://restapi.amap.com/v3/geocode/geo") {
            parameter("key", settings.amapWebKey.trim())
            parameter("address", address)
        }
        if (root.string("status") != "1") throw IllegalStateException(root.amapApiMessage())
        val geocodes = root["geocodes"] as? JsonArray ?: return null
        val first = geocodes.firstOrNull() as? JsonObject ?: return null
        val parts = first.string("location").split(",")
        val lng = parts.getOrNull(0)?.toDoubleOrNull()
        val lat = parts.getOrNull(1)?.toDoubleOrNull()
        return if (lat == null || lng == null) null else LocationDto(latitude = lat, longitude = lng, text = address)
    }

    private suspend fun fetchAroundPois(
        settings: DeveloperSettings,
        keywords: List<String>,
        location: LocationDto,
        radiusMeters: Int
    ): List<JsonObject> {
        val pois = mutableListOf<JsonObject>()
        keywords.distinct().filter { it.isNotBlank() }.forEachIndexed { index, keyword ->
            if (index > 0) delay(300)
            val root = getAmapJson("https://restapi.amap.com/v3/place/around") {
                parameter("key", settings.amapWebKey.trim())
                parameter("location", "${location.longitude},${location.latitude}")
                parameter("keywords", keyword)
                parameter("types", "050000")
                parameter("radius", radiusMeters.coerceIn(500, 10000))
                parameter("offset", 10)
                parameter("page", 1)
                parameter("extensions", "all")
            }
            if (root.string("status") != "1") {
                if (pois.isNotEmpty()) return pois.distinctBy { it.string("id").ifBlank { it.string("name") } }
                throw IllegalStateException(root.amapApiMessage())
            }
            val items = root["pois"] as? JsonArray ?: JsonArray(emptyList())
            pois += items.jsonArray.mapNotNull { it as? JsonObject }
            if (pois.size >= 10) return pois.distinctBy { it.string("id").ifBlank { it.string("name") } }
        }
        return pois.distinctBy { it.string("id").ifBlank { it.string("name") } }
    }

    private suspend fun fetchAroundCandidates(
        settings: DeveloperSettings,
        keyword: String,
        location: LocationDto,
        radiusMeters: Int
    ): List<DirectRestaurantCandidate> {
        if (keyword.isBlank()) return emptyList()
        val root = getAmapJson("https://restapi.amap.com/v3/place/around") {
            parameter("key", settings.amapWebKey.trim())
            parameter("location", "${location.longitude},${location.latitude}")
            parameter("keywords", keyword)
            parameter("types", "050000")
            parameter("radius", radiusMeters.coerceIn(500, 10000))
            parameter("offset", 20)
            parameter("page", 1)
            parameter("extensions", "all")
        }
        if (root.string("status") != "1") {
            throw IllegalStateException(root.amapApiMessage())
        }
        val items = root["pois"] as? JsonArray ?: JsonArray(emptyList())
        return items.jsonArray
            .mapNotNull { it as? JsonObject }
            .mapNotNull { it.toDirectRestaurantCandidate() }
            .distinctBy { it.dedupeKey }
    }

    private suspend fun getAmapJson(
        url: String,
        block: io.ktor.client.request.HttpRequestBuilder.() -> Unit
    ): JsonObject {
        val response = client.get(url, block)
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw IllegalStateException("高德地图 HTTP ${response.status.value}：${text.take(120)}")
        }
        return json.parseToJsonElement(text) as? JsonObject ?: JsonObject(emptyMap())
    }
}

private fun filterAndRankDirectAmapPois(
    pois: List<JsonObject>,
    spec: RestaurantSearchSpec,
    query: String
): List<RestaurantDto> {
    return pois
        .distinctBy { it.string("id").ifBlank { it.string("name") } }
        .mapNotNull { poi ->
            val score = restaurantRelevanceScore(poi.string("name"), poi.string("type"), spec)
            if (spec.strictMatchTerms.isNotEmpty() && score == 0) {
                null
            } else {
                poi.toDirectRestaurantDto(query)?.let { it to score }
            }
        }
        .sortedWith(
            compareByDescending<Pair<RestaurantDto, Int>> { it.second }
                .thenBy { it.first.distance.directDistanceMeters() }
        )
        .map { it.first }
}

private fun JsonObject.toDirectRestaurantCandidate(): DirectRestaurantCandidate? {
    val locationParts = string("location").split(",")
    val lng = locationParts.getOrNull(0)?.toDoubleOrNull()
    val lat = locationParts.getOrNull(1)?.toDoubleOrNull()
    val bizExt = this["biz_ext"] as? JsonObject
    val photos = this["photos"] as? JsonArray
    val photoObjects = photos?.jsonArray?.mapNotNull { it as? JsonObject }.orEmpty()
    val name = string("name")
    if (name.isBlank()) return null
    val address = string("address")
    val type = string("type").ifBlank { "餐饮" }
    val rawDistance = string("distance")
    val distance = rawDistance.toIntOrNull()?.let { "${it}m" }.orEmpty().ifBlank { rawDistance }
    val businessArea = string("business_area")
    val photoTitles = photoObjects.map { it.string("title") }.filter { it.isNotBlank() }
    val rawTags = string("tag")
        .split(Regex("[,，、;；\\s]+"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
    val category = type.substringBefore(";").ifBlank { "餐饮" }
    return DirectRestaurantCandidate(
        id = directRestaurantCandidateId(rawId = string("id"), name = name, address = address),
        name = name,
        type = type,
        address = address,
        distance = distance,
        rating = bizExt?.string("rating").orEmpty(),
        cost = bizExt?.string("cost").orEmpty(),
        businessArea = businessArea,
        tags = (listOf(category, businessArea) + rawTags).filter { it.isNotBlank() }.distinct(),
        description = listOf(type, string("tag")).filter { it.isNotBlank() }.joinToString(" "),
        photoTitles = photoTitles,
        phone = string("tel"),
        coverUrl = photoObjects.firstOrNull()?.string("url").orEmpty(),
        latitude = lat,
        longitude = lng
    )
}

private fun JsonObject.toDirectRestaurantDto(query: String): RestaurantDto? {
    val locationParts = string("location").split(",")
    val lng = locationParts.getOrNull(0)?.toDoubleOrNull()
    val lat = locationParts.getOrNull(1)?.toDoubleOrNull()
    val bizExt = this["biz_ext"] as? JsonObject
    val photos = this["photos"] as? JsonArray
    val photoUrl = (photos?.firstOrNull() as? JsonObject)?.string("url").orEmpty()
    val name = string("name")
    if (name.isBlank()) return null
    val type = string("type").ifBlank { "餐饮" }
    val distance = string("distance").toIntOrNull()?.let { "${it}m" }.orEmpty()
    val rating = bizExt?.string("rating").orEmpty().ifBlank { "暂无" }
    val cost = bizExt?.string("cost").orEmpty()
    val tags = buildList {
        add(type.substringBefore(";").ifBlank { "餐饮" })
        if (query.contains("一个人") || query.contains("一人")) add("单人友好")
        if (query.contains("牛肉面")) add("牛肉面")
        if (query.contains("日料") || query.contains("日本料理") || type.contains("日本料理")) add("日料")
    }.distinct()

    return RestaurantDto(
        id = "amap_${string("id")}",
        source = "amap",
        name = name,
        category = type.substringBefore(";").ifBlank { "餐饮" },
        tags = tags,
        address = string("address"),
        distance = distance.ifBlank { "距离未知" },
        rating = rating,
        price = if (cost.isBlank()) "人均暂无" else "人均 ¥$cost",
        open = "营业状态以地图为准",
        phone = string("tel").ifBlank { "电话暂无" },
        coverUrl = photoUrl,
        reason = "这家店和你的搜索更接近，距离也比较合适。",
        latitude = lat,
        longitude = lng
    )
}

private fun directRestaurantCandidateId(rawId: String, name: String, address: String): String {
    if (rawId.isNotBlank()) return "amap_$rawId"
    val hash = "${name.trim()}|${address.trim()}".hashCode().toString().replace("-", "n")
    return "amap_generated_$hash"
}

private fun amapErrorMessage(error: Throwable): String {
    val message = error.message.orEmpty()
    return friendlyStatusMessage(message.ifBlank { "地图请求失败" }, UserMessageContext.Restaurant)
}

private fun JsonObject.string(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty().trim()

private fun JsonObject.amapApiMessage(): String {
    val info = string("info").ifBlank { "高德地图接口暂时不可用。" }
    val infocode = string("infocode")
    return if (infocode.isBlank()) info else "$info（infocode=$infocode）"
}

private fun String.directDistanceMeters(): Int {
    val value = Regex("""\d+""").find(this)?.value?.toIntOrNull() ?: return Int.MAX_VALUE
    return if (contains("km", ignoreCase = true) || contains("公里")) value * 1000 else value
}
