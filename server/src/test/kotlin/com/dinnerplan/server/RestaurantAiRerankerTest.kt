package com.dinnerplan.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RestaurantAiRerankerTest {
    @Test
    fun fallbackRanksExplicitBuffetBeforePlainSushi() {
        val plan = RestaurantKeywordPlan(
            summary = "用户想找日料自助餐厅",
            keywords = listOf("日料自助", "日本料理", "寿司", "自助餐"),
            mustMatch = listOf("自助", "放题"),
            preferMatch = listOf("日料", "日本料理", "寿司", "刺身", "日式")
        )
        val ranked = RestaurantAiReranker.fallbackRerank(
            query = "日料自助",
            plan = plan,
            candidates = listOf(
                candidate("poi_sushi", "隐泉寿司", "餐饮服务;外国餐厅;日本料理", "220m", "4.9"),
                candidate("poi_buffet", "初花日式放题自助", "餐饮服务;外国餐厅;日本料理", "500m", "4.3")
            ),
            limit = 2
        )

        assertEquals(listOf("poi_buffet", "poi_sushi"), ranked.rankedIds)
    }

    @Test
    fun sanitizedAiResultDropsUnknownAndDuplicateIdsAndAppliesLimit() {
        val candidates = listOf(
            candidate("poi_1", "亲子餐厅", "餐饮服务;中餐厅", "100m", "4.1"),
            candidate("poi_2", "商场家庭餐厅", "餐饮服务;中餐厅", "200m", "4.8")
        )
        val sanitized = RestaurantAiReranker.sanitizeAiResult(
            RestaurantRerankResult(
                rankedIds = listOf("missing", "poi_2", "poi_2", "poi_1"),
                excluded = emptyList(),
                matchReasons = mapOf("poi_2" to listOf("适合家庭")),
                riskWarnings = emptyMap()
            ),
            candidates = candidates,
            limit = 1
        )

        assertEquals(listOf("poi_2"), sanitized.rankedIds)
        assertFalse("missing" in sanitized.rankedIds)
    }

    @Test
    fun fallbackRerankAllowsUpToFiftyRestaurantResults() {
        val candidates = (1..30).map { index ->
            candidate(
                id = "poi_$index",
                name = "家庭餐厅 $index",
                type = "餐饮服务;中餐厅;家庭餐厅",
                distance = "${index * 10}m",
                rating = "4.${index % 10}"
            )
        }

        val rerank = RestaurantAiReranker.fallbackRerank(
            query = "家庭餐厅",
            plan = RestaurantKeywordPlan(preferMatch = listOf("家庭餐厅")),
            candidates = candidates,
            limit = 30
        )

        assertEquals(30, rerank.rankedIds.size)
    }

    @Test
    fun recommendRestaurantsDoesNotCallAiRerankInMainPath() {
        val source = listOf(
            java.nio.file.Path.of("server/src/main/kotlin/com/dinnerplan/server/RecommendationService.kt"),
            java.nio.file.Path.of("src/main/kotlin/com/dinnerplan/server/RecommendationService.kt")
        )
            .first { it.toFile().exists() }
            .toFile()
            .readText()
        val methodBody = source.substringAfter("suspend fun recommendRestaurants")
            .substringBefore("private fun score")

        assertFalse("aiService.rerankRestaurants(" in methodBody)
        assertTrue("RestaurantAiReranker.fallbackRerank" in methodBody)
        assertTrue("aiService.parseRestaurantKeywordPlan" in methodBody)
    }

    @Test
    fun recommendRestaurantsAllowsFiftyResults() {
        val recommendationService = listOf(
            java.nio.file.Path.of("server/src/main/kotlin/com/dinnerplan/server/RecommendationService.kt"),
            java.nio.file.Path.of("src/main/kotlin/com/dinnerplan/server/RecommendationService.kt")
        )
            .first { it.toFile().exists() }
            .toFile()
            .readText()
        val restaurantModules = listOf(
            java.nio.file.Path.of("server/src/main/kotlin/com/dinnerplan/server/RestaurantAiModules.kt"),
            java.nio.file.Path.of("src/main/kotlin/com/dinnerplan/server/RestaurantAiModules.kt")
        )
            .first { it.toFile().exists() }
            .toFile()
            .readText()

        assertTrue("restaurantResultLimit.coerceIn(1, 50)" in recommendationService)
        assertTrue("coerceIn(1, 50)" in restaurantModules)
        assertFalse("coerceIn(1, 20)" in recommendationService)
    }

    private fun candidate(
        id: String,
        name: String,
        type: String,
        distance: String,
        rating: String
    ): RestaurantCandidate {
        return RestaurantCandidate(
            id = id,
            name = name,
            type = type,
            address = "测试地址",
            distance = distance,
            rating = rating,
            cost = "80",
            businessArea = "湖滨",
            tags = listOf(type.substringAfterLast(";")),
            description = "",
            photoTitles = emptyList(),
            photoUrl = "",
            latitude = 30.0,
            longitude = 120.0
        )
    }
}
