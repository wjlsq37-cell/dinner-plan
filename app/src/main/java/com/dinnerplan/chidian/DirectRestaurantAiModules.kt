package com.dinnerplan.chidian

import com.dinnerplan.shared.RestaurantDto
import kotlinx.serialization.Serializable

@Serializable
internal data class DirectRestaurantKeywordPlan(
    val summary: String = "",
    val keywords: List<String> = emptyList(),
    val mustMatch: List<String> = emptyList(),
    val preferMatch: List<String> = emptyList(),
    val negativeMatch: List<String> = emptyList(),
    val searchStrategy: String = "separate"
) {
    fun normalized(query: String): DirectRestaurantKeywordPlan {
        val normalizedKeywords = keywords.cleanTerms().ifEmpty {
            listOf(query.trim()).filter { it.isNotBlank() }.ifEmpty { listOf("餐厅") }
        }
        return copy(
            summary = summary.ifBlank { "用户想找${query.ifBlank { "餐厅" }}" },
            keywords = normalizedKeywords,
            mustMatch = mustMatch.cleanTerms(),
            preferMatch = preferMatch.cleanTerms(),
            negativeMatch = negativeMatch.cleanTerms(),
            searchStrategy = "separate"
        )
    }
}

internal fun broadDirectRestaurantKeywordPlan(): DirectRestaurantKeywordPlan {
    val terms = broadRestaurantSearchTerms()
    return DirectRestaurantKeywordPlan(
        summary = "随机扩大附近餐厅候选范围",
        keywords = terms,
        preferMatch = terms,
        searchStrategy = "separate"
    ).normalized("")
}

@Serializable
internal data class DirectRestaurantExcluded(
    val id: String = "",
    val reason: String = ""
)

@Serializable
internal data class DirectRestaurantRerankResult(
    val rankedIds: List<String> = emptyList(),
    val excluded: List<DirectRestaurantExcluded> = emptyList(),
    val matchReasons: Map<String, List<String>> = emptyMap(),
    val riskWarnings: Map<String, List<String>> = emptyMap()
)

@Serializable
internal data class DirectRestaurantCandidate(
    val id: String,
    val name: String,
    val type: String = "",
    val address: String = "",
    val distance: String = "",
    val rating: String = "",
    val cost: String = "",
    val businessArea: String = "",
    val tags: List<String> = emptyList(),
    val description: String = "",
    val photoTitles: List<String> = emptyList(),
    val phone: String = "",
    val coverUrl: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null
) {
    val dedupeKey: String
        get() = id.ifBlank { "${name.trim()}|${address.trim()}".lowercase() }

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

    fun distanceMeters(): Int {
        val number = Regex("""\d+(\.\d+)?""").find(distance)?.value?.toDoubleOrNull() ?: return Int.MAX_VALUE
        return if (distance.contains("km", ignoreCase = true) || distance.contains("公里")) {
            (number * 1000).toInt()
        } else {
            number.toInt()
        }
    }

    fun ratingValue(): Double = rating.toDoubleOrNull() ?: 0.0

    fun toRestaurantDto(matchReasons: List<String>, riskWarnings: List<String>): RestaurantDto {
        val category = type.substringBefore(";").ifBlank { "餐饮" }
        val priceText = if (cost.isBlank()) "人均暂无" else if (cost.startsWith("人均")) cost else "人均 ¥$cost"
        val distanceText = distance.ifBlank { "距离未知" }
        val reasonText = buildList {
            if (matchReasons.isNotEmpty()) add("这家店和你的搜索更接近")
            if (distanceText != "距离未知") add("距离也比较合适")
            if (ratingValue() >= 4.5) add("评分较高")
            if (riskWarnings.isNotEmpty()) add("建议出发前再确认店内信息")
        }.ifEmpty {
            listOf("这家店和你的搜索更接近，距离也比较合适")
        }.joinToString("，") + "。"
        return RestaurantDto(
            id = id,
            source = "amap",
            name = name,
            category = category,
            tags = tags.ifEmpty { listOf(category) },
            address = address,
            distance = distanceText,
            rating = rating.ifBlank { "暂无" },
            price = priceText,
            open = "营业状态以地图为准",
            phone = phone.ifBlank { "电话暂无" },
            coverUrl = coverUrl,
            reason = reasonText,
            latitude = latitude,
            longitude = longitude
        )
    }
}

internal object DirectRestaurantKeywordAiParser {
    fun fallbackPlan(query: String): DirectRestaurantKeywordPlan {
        val normalized = query.trim()
        return when {
            (normalized.contains("日料") || normalized.contains("日本") || normalized.contains("寿司")) &&
                (normalized.contains("自助") || normalized.contains("放题")) ->
                DirectRestaurantKeywordPlan(
                    summary = "用户想找日料自助餐厅",
                    keywords = listOf("日料自助", "日本料理", "日式放题", "寿司", "自助餐"),
                    mustMatch = listOf("自助", "放题"),
                    preferMatch = listOf("日料", "日本料理", "寿司", "刺身", "日式")
                )

            normalized.contains("带娃") || normalized.contains("亲子") || normalized.contains("儿童") || normalized.contains("孩子") ->
                DirectRestaurantKeywordPlan(
                    summary = "用户想找适合带孩子吃饭的餐厅",
                    keywords = listOf("亲子餐厅", "儿童友好", "商场餐饮", "家庭餐厅", "清淡餐厅"),
                    preferMatch = listOf("亲子", "儿童", "家庭", "商场", "清淡", "包间"),
                    negativeMatch = listOf("酒吧", "夜店")
                )

            normalized.contains("清淡") ->
                DirectRestaurantKeywordPlan(
                    summary = "用户想找清淡口味的附近餐厅",
                    keywords = listOf("清淡餐厅", "粤菜", "汤粉", "粥", "蒸菜"),
                    preferMatch = listOf("清淡", "粥", "汤", "蒸", "粤菜"),
                    negativeMatch = listOf("火锅", "烧烤", "麻辣", "重辣")
                )

            normalized.contains("不辣") && normalized.contains("川") ->
                DirectRestaurantKeywordPlan(
                    summary = "用户想找不辣或可少辣的川菜",
                    keywords = listOf("川菜", "家常川菜", "不辣川菜", "江湖菜", "中餐"),
                    mustMatch = listOf("川"),
                    preferMatch = listOf("不辣", "少辣", "家常", "中餐"),
                    negativeMatch = listOf("重辣", "麻辣")
                )

            normalized.contains("约会") || normalized.contains("西餐") ->
                DirectRestaurantKeywordPlan(
                    summary = "用户想找适合约会的西餐",
                    keywords = listOf("西餐", "牛排", "意大利餐厅", "约会餐厅", "餐酒馆"),
                    preferMatch = listOf("西餐", "牛排", "意面", "环境", "约会", "餐酒馆")
                )

            else ->
                DirectRestaurantKeywordPlan(
                    summary = "用户想找附近餐厅",
                    keywords = listOf(normalized.ifBlank { "餐厅" }),
                    preferMatch = normalized.splitTerms()
                )
        }.normalized(normalized)
    }
}

internal class DirectAmapMultiKeywordSearch(
    private val fetcher: suspend (String) -> List<DirectRestaurantCandidate>
) {
    suspend fun search(plan: DirectRestaurantKeywordPlan): List<DirectRestaurantCandidate> {
        val merged = mutableListOf<DirectRestaurantCandidate>()
        plan.keywords.cleanTerms().forEach { keyword ->
            val candidates = runCatching { fetcher(keyword) }.getOrDefault(emptyList())
            merged += candidates
        }
        return merged.distinctBy { it.dedupeKey }
    }
}

internal object DirectRestaurantAiReranker {
    fun fallbackRerank(
        query: String,
        plan: DirectRestaurantKeywordPlan,
        candidates: List<DirectRestaurantCandidate>,
        limit: Int
    ): DirectRestaurantRerankResult {
        val normalizedPlan = plan.normalized(query)
        val scored = candidates.map { candidate ->
            val text = candidate.searchableText()
            val mustHits = normalizedPlan.mustMatch.filter { text.contains(it, ignoreCase = true) }
            val preferHits = normalizedPlan.preferMatch.filter { text.contains(it, ignoreCase = true) }
            val negativeHits = normalizedPlan.negativeMatch.filter { text.contains(it, ignoreCase = true) }
            val score = mustHits.size * 120 +
                preferHits.size * 25 +
                (if (query.isNotBlank() && text.contains(query, ignoreCase = true)) 8 else 0) +
                distanceScore(candidate.distanceMeters()) +
                (candidate.ratingValue() * 4).toInt() -
                negativeHits.size * 100
            candidate to DirectRestaurantScore(score, mustHits, preferHits, negativeHits)
        }.sortedWith(
            compareByDescending<Pair<DirectRestaurantCandidate, DirectRestaurantScore>> { it.second.score }
                .thenByDescending { it.first.ratingValue() }
                .thenBy { it.first.distanceMeters() }
        )

        val selected = scored.take(limit.coerceIn(1, 50))
        return DirectRestaurantRerankResult(
            rankedIds = selected.map { it.first.id },
            excluded = scored.drop(limit.coerceIn(1, 50)).map { (candidate, score) ->
                DirectRestaurantExcluded(
                    id = candidate.id,
                    reason = if (score.negativeHits.isNotEmpty()) {
                        "包含本次不想要的关键词：${score.negativeHits.joinToString("、")}"
                    } else {
                        "和本次需求的贴合度相对较低"
                    }
                )
            },
            matchReasons = selected.associate { (candidate, score) ->
                candidate.id to fallbackReasons(candidate, normalizedPlan, score)
            },
            riskWarnings = selected.associate { (candidate, score) ->
                candidate.id to fallbackWarnings(normalizedPlan, score)
            }.filterValues { it.isNotEmpty() }
        )
    }

    fun sanitizeAiResult(
        result: DirectRestaurantRerankResult,
        candidates: List<DirectRestaurantCandidate>,
        limit: Int
    ): DirectRestaurantRerankResult {
        val ids = candidates.map { it.id }.toSet()
        val rankedIds = result.rankedIds.filter { it in ids }.distinct().take(limit.coerceIn(1, 50))
        return result.copy(
            rankedIds = rankedIds,
            excluded = result.excluded.filter { it.id in ids },
            matchReasons = result.matchReasons.filterKeys { it in ids },
            riskWarnings = result.riskWarnings.filterKeys { it in ids }
        )
    }

    fun toRestaurants(
        candidates: List<DirectRestaurantCandidate>,
        rerank: DirectRestaurantRerankResult,
        limit: Int
    ): List<RestaurantDto> {
        val byId = candidates.associateBy { it.id }
        return rerank.rankedIds
            .take(limit.coerceIn(1, 50))
            .mapNotNull { id ->
                byId[id]?.toRestaurantDto(
                    matchReasons = rerank.matchReasons[id].orEmpty(),
                    riskWarnings = rerank.riskWarnings[id].orEmpty()
                )
            }
    }

    private fun fallbackReasons(
        candidate: DirectRestaurantCandidate,
        plan: DirectRestaurantKeywordPlan,
        score: DirectRestaurantScore
    ): List<String> {
        return buildList {
            if (score.mustHits.isNotEmpty()) add("包含${score.mustHits.joinToString("/")}")
            if (score.preferHits.isNotEmpty()) add("包含${score.preferHits.take(3).joinToString("/")}")
            if (candidate.distanceMeters() <= 1500) add("距离较近")
            if (candidate.ratingValue() >= 4.5) add("评分较高")
            if (isEmpty()) add("候选信息与本次搜索较接近")
            if (plan.searchStrategy != "separate") add("已综合多组搜索词筛选")
        }
    }

    private fun fallbackWarnings(
        plan: DirectRestaurantKeywordPlan,
        score: DirectRestaurantScore
    ): List<String> {
        return buildList {
            if (plan.mustMatch.isNotEmpty() && score.mustHits.isEmpty()) {
                add("候选信息不足，建议到店前确认是否满足关键需求")
            }
        }
    }

    private fun distanceScore(distanceMeters: Int): Int {
        return when {
            distanceMeters <= 500 -> 30
            distanceMeters <= 1000 -> 20
            distanceMeters <= 2000 -> 12
            distanceMeters <= 5000 -> 6
            else -> 0
        }
    }
}

private data class DirectRestaurantScore(
    val score: Int,
    val mustHits: List<String>,
    val preferHits: List<String>,
    val negativeHits: List<String>
)

private fun List<String>.cleanTerms(): List<String> {
    return flatMap { it.splitTerms() }
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
}

private fun broadRestaurantSearchTerms(): List<String> {
    return listOf("餐厅", "中餐", "小吃", "快餐", "面馆", "粉面", "火锅", "烧烤", "日料", "西餐", "甜品", "咖啡")
}

private fun String.splitTerms(): List<String> {
    return split(Regex("[,，、/;；\\s]+"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
}
