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

    suspend fun searchRestaurants(
        settings: DeveloperSettings,
        query: String,
        locationText: String,
        location: LocationDto,
        preferences: UserPreferenceDto
    ): RestaurantRecommendationResponse {
        if (settings.amapWebKey.isBlank()) {
            return RestaurantRecommendationResponse(
                restaurants = emptyList(),
                locationUsed = LocationDto(text = locationText),
                fallbackReason = "开发者模式已开启，但高德 Web Key 未填写。"
            )
        }
        return try {
            withTimeout(settings.maxWaitMillis) {
                val resolvedLocation = resolveLocation(settings, location)
                    ?: return@withTimeout RestaurantRecommendationResponse(
                        restaurants = emptyList(),
                        locationUsed = location,
                        fallbackReason = "没有可用位置，请手动输入城市、商圈或地标。"
                    )

                val spec = RestaurantSearchSpec.fromQuery(query)
                val pois = fetchAroundPois(
                    settings = settings,
                    keywords = spec.primaryKeywords,
                    location = resolvedLocation,
                    radiusMeters = preferences.defaultDistanceKm * 1000
                )
                val matched = filterAndRankDirectAmapPois(pois, spec, query)
                if (matched.isNotEmpty()) {
                    return@withTimeout RestaurantRecommendationResponse(matched, resolvedLocation, null)
                }
                if (spec.useGenericFallback) {
                    val fallbackPois = fetchAroundPois(
                        settings = settings,
                        keywords = listOf("餐厅"),
                        location = resolvedLocation,
                        radiusMeters = preferences.defaultDistanceKm * 1000
                    )
                    val fallback = filterAndRankDirectAmapPois(fallbackPois, RestaurantSearchSpec.generic(), query)
                    return@withTimeout RestaurantRecommendationResponse(
                        restaurants = fallback,
                        locationUsed = resolvedLocation,
                        fallbackReason = if (fallback.isEmpty()) {
                            "附近未找到日料相关店铺，也暂未找到附近餐厅。"
                        } else {
                            spec.genericFallbackReason
                        }
                    )
                }
                RestaurantRecommendationResponse(
                    restaurants = emptyList(),
                    locationUsed = resolvedLocation,
                    fallbackReason = "附近暂未找到符合条件的真实餐厅。"
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
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
        reason = "来自高德 POI：距离 ${distance.ifBlank { "未知" }}，类型匹配 ${type.substringBefore(";")}。",
        latitude = lat,
        longitude = lng
    )
}

private fun amapErrorMessage(error: Throwable): String {
    val message = error.message.orEmpty()
    return when {
        message.contains("INVALID_USER_KEY", ignoreCase = true) ||
            message.contains("INVALID_USER_SCODE", ignoreCase = true) ||
            message.contains("USERKEY_PLAT_NOMATCH", ignoreCase = true) ||
            message.contains("key", ignoreCase = true) ->
            "高德 Web Key 不正确、Key 类型不匹配或未开通 Web 服务：${message.ifBlank { "未知错误" }}"
        message.contains("timeout", ignoreCase = true) || error is kotlinx.coroutines.TimeoutCancellationException ->
            "高德地图请求超时，请调大最大等待时长或稍后重试。"
        else -> "高德地图请求失败：${message.ifBlank { "未知错误" }}"
    }
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
