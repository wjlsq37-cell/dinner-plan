package com.dinnerplan.server

import com.dinnerplan.shared.LocationDto
import com.dinnerplan.shared.RestaurantDto
import com.dinnerplan.shared.RestaurantSearchSpec
import com.dinnerplan.shared.restaurantRelevanceScore
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

class AmapService(
    private val config: AppConfig,
    private val json: Json
) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
    }

    suspend fun searchRestaurants(
        query: String,
        location: LocationDto?,
        radiusMeters: Int
    ): AmapRestaurantSearchResult {
        if (!config.amapConfigured) {
            return AmapRestaurantSearchResult(
                restaurants = emptyList(),
                locationUsed = location,
                fallbackReason = "相关服务还没配置好，请到设置中检查后再试。"
            )
        }

        val resolvedLocation = resolveLocation(location)
            ?: return AmapRestaurantSearchResult(
                restaurants = emptyList(),
                locationUsed = location,
                fallbackReason = "暂时无法获取当前位置，你也可以手动输入地点。"
            )

        val spec = RestaurantSearchSpec.fromQuery(query)
        return runCatching {
            val primary = fetchAroundPois(
                keywords = spec.primaryKeywords,
                location = resolvedLocation,
                radiusMeters = radiusMeters
            )
            if (primary.fallbackReason != null) {
                return@runCatching AmapRestaurantSearchResult(
                    restaurants = emptyList(),
                    locationUsed = resolvedLocation,
                    fallbackReason = primary.fallbackReason
                )
            }

            val matchedRestaurants = filterAndRankRestaurantPois(primary.pois, spec, query)
            if (matchedRestaurants.isNotEmpty()) {
                return@runCatching AmapRestaurantSearchResult(
                    restaurants = matchedRestaurants,
                    locationUsed = resolvedLocation,
                    fallbackReason = null
                )
            }

            if (spec.useGenericFallback) {
                val fallback = fetchAroundPois(
                    keywords = listOf("餐厅"),
                    location = resolvedLocation,
                    radiusMeters = radiusMeters
                )
                if (fallback.fallbackReason != null) {
                    return@runCatching AmapRestaurantSearchResult(
                        restaurants = emptyList(),
                        locationUsed = resolvedLocation,
                        fallbackReason = fallback.fallbackReason
                    )
                }
                val fallbackRestaurants = filterAndRankRestaurantPois(
                    pois = fallback.pois,
                    spec = RestaurantSearchSpec.generic(),
                    query = query
                )
                return@runCatching AmapRestaurantSearchResult(
                    restaurants = fallbackRestaurants,
                    locationUsed = resolvedLocation,
                    fallbackReason = if (fallbackRestaurants.isEmpty()) {
                        "暂时没找到合适的结果，可以换个关键词或放宽条件再试。"
                    } else {
                        spec.genericFallbackReason
                    }
                )
            }

            AmapRestaurantSearchResult(
                restaurants = emptyList(),
                locationUsed = resolvedLocation,
                fallbackReason = "暂时没找到合适的结果，可以换个关键词或放宽条件再试。"
            )
        }.getOrElse { error ->
            AmapRestaurantSearchResult(
                restaurants = emptyList(),
                locationUsed = resolvedLocation,
                fallbackReason = "附近餐厅数据暂时加载失败，请稍后再试。"
            )
        }
    }

    suspend fun searchRestaurantCandidates(
        plan: RestaurantKeywordPlan,
        location: LocationDto?,
        radiusMeters: Int
    ): AmapRestaurantCandidateSearchResult {
        if (!config.amapConfigured) {
            return AmapRestaurantCandidateSearchResult(
                candidates = emptyList(),
                locationUsed = location,
                fallbackReason = "相关服务还没配置好，请到设置中检查后再试。"
            )
        }

        val resolvedLocation = resolveLocation(location)
            ?: return AmapRestaurantCandidateSearchResult(
                candidates = emptyList(),
                locationUsed = location,
                fallbackReason = "暂时无法获取当前位置，你也可以手动输入地点。"
            )

        val fallbackReasons = mutableListOf<String>()
        val search = AmapMultiKeywordSearch { keyword ->
            val fetched = fetchAroundPois(
                keywords = listOf(keyword),
                location = resolvedLocation,
                radiusMeters = radiusMeters
            )
            fetched.fallbackReason?.let { fallbackReasons += it }
            fetched.pois.mapNotNull { it.toRestaurantCandidate() }
        }
        val candidates = runCatching { search.search(plan) }.getOrElse { error ->
            fallbackReasons += "附近餐厅数据暂时加载失败，请稍后再试。"
            emptyList()
        }

        return AmapRestaurantCandidateSearchResult(
            candidates = candidates,
            locationUsed = resolvedLocation,
            fallbackReason = when {
                candidates.isNotEmpty() -> null
                fallbackReasons.isNotEmpty() -> fallbackReasons.first()
                else -> "暂时没找到合适的结果，可以换个关键词或放宽条件再试。"
            }
        )
    }

    private suspend fun fetchAroundPois(
        keywords: List<String>,
        location: LocationDto,
        radiusMeters: Int
    ): AmapPoiFetchResult {
        val pois = mutableListOf<JsonObject>()
        keywords.distinct().filter { it.isNotBlank() }.forEachIndexed { index, keyword ->
            if (index > 0) delay(300)
            val response = client.get("https://restapi.amap.com/v3/place/around") {
                parameter("key", config.amapWebKey)
                parameter("location", "${location.longitude},${location.latitude}")
                parameter("keywords", keyword)
                parameter("types", "050000")
                parameter("radius", radiusMeters.coerceIn(500, 10000))
                parameter("offset", 10)
                parameter("page", 1)
                parameter("extensions", "all")
            }.body<JsonObject>()

            val status = response.string("status")
            if (status != "1") {
                return if (pois.isNotEmpty()) {
                    AmapPoiFetchResult(
                        pois = pois.distinctBy { it.string("id").ifBlank { it.string("name") } },
                        fallbackReason = null
                    )
                } else {
                    AmapPoiFetchResult(
                        pois = emptyList(),
                        fallbackReason = response.string("info").ifBlank { "高德地图接口暂时不可用。" }
                    )
                }
            }

            val items = response["pois"] as? JsonArray ?: JsonArray(emptyList())
            pois += items.jsonArray.mapNotNull { it as? JsonObject }
            if (pois.size >= 10) {
                return AmapPoiFetchResult(
                    pois = pois.distinctBy { it.string("id").ifBlank { it.string("name") } },
                    fallbackReason = null
                )
            }
        }

        return AmapPoiFetchResult(
            pois = pois.distinctBy { it.string("id").ifBlank { it.string("name") } },
            fallbackReason = null
        )
    }

    private suspend fun resolveLocation(location: LocationDto?): LocationDto? {
        if (location?.latitude != null && location.longitude != null) return location
        val text = location?.text?.takeIf { it.isNotBlank() } ?: "上海人民广场"
        return runCatching {
            val response = client.get("https://restapi.amap.com/v3/geocode/geo") {
                parameter("key", config.amapWebKey)
                parameter("address", text)
            }.body<JsonObject>()
            val geocodes = response["geocodes"] as? JsonArray ?: return null
            val first = geocodes.firstOrNull() as? JsonObject ?: return null
            val parts = first.string("location").split(",")
            val lng = parts.getOrNull(0)?.toDoubleOrNull()
            val lat = parts.getOrNull(1)?.toDoubleOrNull()
            if (lat == null || lng == null) null else LocationDto(latitude = lat, longitude = lng, text = text)
        }.getOrNull()
    }
}

internal fun filterAndRankRestaurantPois(
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
                poi.toRestaurantDto(query)?.let { it to score }
            }
        }
        .sortedWith(
            compareByDescending<Pair<RestaurantDto, Int>> { it.second }
                .thenBy { it.first.distance.distanceMeters() }
        )
        .map { it.first }
}

private fun JsonObject.toRestaurantDto(query: String): RestaurantDto? {
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
        if (query.contains("一人") || query.contains("一个人")) add("单人友好")
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
        phone = string("tel").ifBlank { "暂无电话" },
        coverUrl = photoUrl.ifBlank { "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?auto=format&fit=crop&w=900&q=80" },
        reason = "这家店和你的搜索更接近，距离也比较合适。",
        latitude = lat,
        longitude = lng
    )
}

private fun JsonObject.toRestaurantCandidate(): RestaurantCandidate? {
    val locationParts = string("location").split(",")
    val lng = locationParts.getOrNull(0)?.toDoubleOrNull()
    val lat = locationParts.getOrNull(1)?.toDoubleOrNull()
    val bizExt = this["biz_ext"] as? JsonObject
    val photos = this["photos"] as? JsonArray
    val firstPhoto = photos?.firstOrNull() as? JsonObject
    val name = string("name")
    if (name.isBlank()) return null
    val type = string("type").ifBlank { "餐饮" }
    val distance = string("distance").toIntOrNull()?.let { "${it}m" }.orEmpty()
    val cost = bizExt?.string("cost").orEmpty()
    val tags = buildList {
        add(type.substringBefore(";").ifBlank { "餐饮" })
        type.split(";").drop(1).mapNotNullTo(this) { it.takeIf(String::isNotBlank) }
    }.distinct()
    return RestaurantCandidate(
        id = "amap_${string("id").ifBlank { stableCandidateId(name, string("address")) }}",
        name = name,
        type = type,
        address = string("address"),
        distance = distance,
        rating = bizExt?.string("rating").orEmpty().ifBlank { "暂无" },
        cost = cost,
        businessArea = string("business_area"),
        tags = tags,
        description = string("tag"),
        photoTitles = photos?.mapNotNull { (it as? JsonObject)?.string("title")?.takeIf(String::isNotBlank) }.orEmpty(),
        photoUrl = firstPhoto?.string("url").orEmpty(),
        latitude = lat,
        longitude = lng
    )
}

private fun String.distanceMeters(): Int {
    return removeSuffix("m").toIntOrNull() ?: Int.MAX_VALUE
}

private fun JsonObject.string(name: String): String {
    return runCatching { this[name]?.jsonPrimitive?.contentOrNull.orEmpty() }.getOrDefault("")
}

data class AmapRestaurantSearchResult(
    val restaurants: List<RestaurantDto>,
    val locationUsed: LocationDto?,
    val fallbackReason: String?
)

data class AmapRestaurantCandidateSearchResult(
    val candidates: List<RestaurantCandidate>,
    val locationUsed: LocationDto?,
    val fallbackReason: String?
)

private data class AmapPoiFetchResult(
    val pois: List<JsonObject>,
    val fallbackReason: String?
)

private fun stableCandidateId(name: String, address: String): String {
    return "${name}|${address}".hashCode().toString().replace("-", "n")
}
