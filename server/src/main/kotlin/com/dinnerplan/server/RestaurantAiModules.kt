package com.dinnerplan.server

import com.dinnerplan.shared.RestaurantDto
import kotlinx.serialization.Serializable

@Serializable
data class RestaurantKeywordPlan(
    val summary: String = "",
    val keywords: List<String> = emptyList(),
    val mustMatch: List<String> = emptyList(),
    val preferMatch: List<String> = emptyList(),
    val negativeMatch: List<String> = emptyList(),
    val searchStrategy: String = "separate"
) {
    fun normalized(): RestaurantKeywordPlan {
        val cleanKeywords = keywords.cleanTerms().ifEmpty { listOf("餐厅") }
        return copy(
            keywords = cleanKeywords.take(8),
            mustMatch = mustMatch.cleanTerms().take(8),
            preferMatch = preferMatch.cleanTerms().take(12),
            negativeMatch = negativeMatch.cleanTerms().take(8),
            searchStrategy = if (searchStrategy.isBlank()) "separate" else searchStrategy
        )
    }
}

@Serializable
data class RestaurantRerankResult(
    val rankedIds: List<String> = emptyList(),
    val excluded: List<RestaurantExcluded> = emptyList(),
    val matchReasons: Map<String, List<String>> = emptyMap(),
    val riskWarnings: Map<String, List<String>> = emptyMap()
)

@Serializable
data class RestaurantExcluded(
    val id: String,
    val reason: String
)

@Serializable
data class RestaurantCandidate(
    val id: String,
    val name: String,
    val type: String,
    val address: String,
    val distance: String,
    val rating: String,
    val cost: String,
    val businessArea: String,
    val tags: List<String>,
    val description: String,
    val photoTitles: List<String>,
    val photoUrl: String,
    val latitude: Double? = null,
    val longitude: Double? = null
) {
    val dedupeKey: String
        get() = id.ifBlank { "${name.trim()}|${address.trim()}" }

    fun searchableText(): String {
        return listOf(
            name,
            type,
            address,
            businessArea,
            tags.joinToString(" "),
            description,
            photoTitles.joinToString(" ")
        ).joinToString(" ")
    }

    fun toRestaurantDto(matchReasons: List<String>, riskWarnings: List<String>): RestaurantDto {
        val reasonText = buildList {
            if (matchReasons.isNotEmpty()) add("这家店和你的搜索更接近")
            if (distance.isNotBlank()) add("距离也比较合适")
            if (riskWarnings.isNotEmpty()) add("建议出发前再确认店内信息")
        }.ifEmpty {
            listOf("这家店和你的搜索更接近，距离也比较合适")
        }.joinToString("，") + "。"
        return RestaurantDto(
            id = id,
            source = "amap",
            name = name,
            category = type.substringBefore(";").ifBlank { "餐饮" },
            tags = tags.ifEmpty { listOf(type.substringBefore(";").ifBlank { "餐饮" }) },
            address = address,
            distance = distance.ifBlank { "距离未知" },
            rating = rating.ifBlank { "暂无" },
            price = if (cost.isBlank()) "人均暂无" else "人均 ¥$cost",
            open = "营业状态以地图为准",
            phone = "",
            coverUrl = photoUrl.ifBlank { "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?auto=format&fit=crop&w=900&q=80" },
            reason = reasonText,
            latitude = latitude,
            longitude = longitude
        )
    }
}

object RestaurantKeywordAiParser {
    fun fallbackPlan(query: String): RestaurantKeywordPlan {
        val trimmed = query.trim()
        return when {
            trimmed.contains("日料") || trimmed.contains("日本") || trimmed.contains("寿司") -> {
                val buffet = trimmed.contains("自助") || trimmed.contains("放题")
                RestaurantKeywordPlan(
                    summary = if (buffet) "用户想找日料自助餐厅" else "用户想找日料餐厅",
                    keywords = if (buffet) {
                        listOf("日料自助", "日本料理", "日式放题", "寿司", "自助餐")
                    } else {
                        listOf("日本料理", "日料", "寿司", "居酒屋", "日式拉面")
                    },
                    mustMatch = if (buffet) listOf("自助", "放题") else emptyList(),
                    preferMatch = listOf("日料", "日本料理", "寿司", "刺身", "日式"),
                    searchStrategy = "separate"
                )
            }
            trimmed.contains("带娃") || trimmed.contains("孩子") || trimmed.contains("儿童") || trimmed.contains("亲子") ->
                RestaurantKeywordPlan(
                    summary = "用户想找适合带娃吃饭的餐厅",
                    keywords = listOf("亲子餐厅", "儿童友好", "商场餐饮", "家庭餐厅", "清淡餐厅"),
                    preferMatch = listOf("亲子", "儿童", "家庭", "商场", "清淡", "包间"),
                    negativeMatch = listOf("酒吧", "夜店"),
                    searchStrategy = "separate"
                )
            trimmed.contains("清淡") ->
                RestaurantKeywordPlan(
                    summary = "用户想找清淡口味餐厅",
                    keywords = listOf("清淡餐厅", "粤菜", "汤粉", "粥", "蒸菜"),
                    preferMatch = listOf("清淡", "粤菜", "粥", "汤", "蒸"),
                    negativeMatch = listOf("麻辣", "重辣"),
                    searchStrategy = "separate"
                )
            trimmed.contains("不辣") && trimmed.contains("川菜") ->
                RestaurantKeywordPlan(
                    summary = "用户想找不辣川菜或有不辣菜的川菜餐厅",
                    keywords = listOf("川菜", "家常川菜", "不辣川菜"),
                    mustMatch = listOf("川菜"),
                    preferMatch = listOf("不辣", "家常", "清淡"),
                    negativeMatch = listOf("麻辣", "重辣"),
                    searchStrategy = "separate"
                )
            trimmed.contains("约会") || trimmed.contains("西餐") ->
                RestaurantKeywordPlan(
                    summary = "用户想找适合约会的西餐厅",
                    keywords = listOf("西餐厅", "约会餐厅", "牛排", "意大利餐厅"),
                    preferMatch = listOf("西餐", "牛排", "意大利", "环境", "约会"),
                    searchStrategy = "separate"
                )
            else ->
                RestaurantKeywordPlan(
                    summary = "用户想找附近餐厅",
                    keywords = listOf(trimmed.ifBlank { "餐厅" }),
                    searchStrategy = "separate"
                )
        }.normalized()
    }
}

class AmapMultiKeywordSearch(
    private val fetcher: suspend (String) -> List<RestaurantCandidate>
) {
    suspend fun search(plan: RestaurantKeywordPlan): List<RestaurantCandidate> {
        val candidates = mutableListOf<RestaurantCandidate>()
        plan.normalized().keywords.forEach { keyword ->
            val fetched = runCatching { fetcher(keyword) }.getOrDefault(emptyList())
            candidates += fetched
        }
        return candidates.distinctBy { it.dedupeKey }
    }
}

object RestaurantAiReranker {
    fun fallbackRerank(
        query: String,
        plan: RestaurantKeywordPlan,
        candidates: List<RestaurantCandidate>,
        limit: Int
    ): RestaurantRerankResult {
        val ranked = candidates
            .map { candidate -> candidate to scoreCandidate(query, plan.normalized(), candidate) }
            .sortedWith(
                compareByDescending<Pair<RestaurantCandidate, Int>> { it.second }
                    .thenByDescending { parseRating(it.first.rating) ?: -1.0 }
                    .thenBy { parseDistanceMeters(it.first.distance) ?: Int.MAX_VALUE }
            )
            .map { it.first }
            .take(limit.coerceIn(1, 50))
        return RestaurantRerankResult(
            rankedIds = ranked.map { it.id },
            excluded = candidates.filterNot { candidate -> ranked.any { it.id == candidate.id } }
                .map { RestaurantExcluded(it.id, "程序兜底排序后未进入前 $limit") },
            matchReasons = ranked.associate { it.id to matchReasons(plan, it) },
            riskWarnings = ranked
                .filter { it.searchableText().isBlank() || matchReasons(plan, it).isEmpty() }
                .associate { it.id to listOf("候选信息不足，建议到店前确认") }
        )
    }

    fun sanitizeAiResult(
        result: RestaurantRerankResult,
        candidates: List<RestaurantCandidate>,
        limit: Int
    ): RestaurantRerankResult {
        val validIds = candidates.map { it.id }.toSet()
        val rankedIds = result.rankedIds
            .filter { it in validIds }
            .distinct()
            .take(limit.coerceIn(1, 50))
        return result.copy(
            rankedIds = rankedIds,
            excluded = result.excluded.filter { it.id in validIds },
            matchReasons = result.matchReasons.filterKeys { it in validIds },
            riskWarnings = result.riskWarnings.filterKeys { it in validIds }
        )
    }

    fun toRestaurants(
        candidates: List<RestaurantCandidate>,
        rerank: RestaurantRerankResult,
        limit: Int
    ): List<RestaurantDto> {
        val byId = candidates.associateBy { it.id }
        return rerank.rankedIds
            .distinct()
            .take(limit.coerceIn(1, 50))
            .mapNotNull { id ->
                byId[id]?.toRestaurantDto(
                    matchReasons = rerank.matchReasons[id].orEmpty(),
                    riskWarnings = rerank.riskWarnings[id].orEmpty()
                )
            }
    }

    private fun scoreCandidate(query: String, plan: RestaurantKeywordPlan, candidate: RestaurantCandidate): Int {
        val text = candidate.searchableText()
        val mustScore = plan.mustMatch.fold(0) { total, term -> total + if (text.contains(term, ignoreCase = true)) 120 else 0 }
        val preferScore = plan.preferMatch.fold(0) { total, term -> total + if (text.contains(term, ignoreCase = true)) 25 else 0 }
        val negativeScore = plan.negativeMatch.fold(0) { total, term -> total + if (text.contains(term, ignoreCase = true)) 100 else 0 }
        val queryScore = splitTerms(query).fold(0) { total, term -> total + if (text.contains(term, ignoreCase = true)) 8 else 0 }
        val ratingScore = ((parseRating(candidate.rating) ?: 0.0) * 4).toInt()
        val distanceScore = when (parseDistanceMeters(candidate.distance) ?: Int.MAX_VALUE) {
            in 0..300 -> 12
            in 301..800 -> 8
            in 801..1500 -> 4
            else -> 0
        }
        return mustScore + preferScore + queryScore + ratingScore + distanceScore - negativeScore
    }

    private fun matchReasons(plan: RestaurantKeywordPlan, candidate: RestaurantCandidate): List<String> {
        val text = candidate.searchableText()
        return buildList {
            plan.mustMatch.filter { text.contains(it, ignoreCase = true) }.forEach { add("包含$it") }
            plan.preferMatch.filter { text.contains(it, ignoreCase = true) }.take(3).forEach { add("包含$it") }
            parseDistanceMeters(candidate.distance)?.let { if (it <= 800) add("距离较近") }
        }.distinct()
    }
}

private fun List<String>.cleanTerms(): List<String> {
    return flatMap(::splitTerms).filter { it.isNotBlank() }.distinct()
}

private fun splitTerms(value: String): List<String> {
    return value.split(Regex("[,，、/;；\\s]+")).map { it.trim() }.filter { it.isNotBlank() }
}

private fun parseRating(value: String): Double? {
    return Regex("""\d+(\.\d+)?""").find(value)?.value?.toDoubleOrNull()
}

private fun parseDistanceMeters(value: String): Int? {
    val number = Regex("""\d+(\.\d+)?""").find(value)?.value?.toDoubleOrNull() ?: return null
    return if (value.contains("km", ignoreCase = true) || value.contains("公里")) {
        (number * 1000).toInt()
    } else {
        number.toInt()
    }
}
