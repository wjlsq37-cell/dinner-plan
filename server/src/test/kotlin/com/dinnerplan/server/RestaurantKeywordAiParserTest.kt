package com.dinnerplan.server

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RestaurantKeywordAiParserTest {
    @Test
    fun japaneseBuffetFallbackProducesSeparateSearchKeywords() {
        val plan = RestaurantKeywordAiParser.fallbackPlan("日料自助")

        assertTrue(plan.keywords.size >= 4)
        assertTrue("日料自助" in plan.keywords)
        assertTrue(plan.keywords.any { it.contains("日本料理") || it.contains("日料") })
        assertTrue(plan.keywords.any { it.contains("寿司") })
        assertTrue(plan.mustMatch.any { it.contains("自助") || it.contains("放题") })
        assertEqualsNoJoinedKeyword(plan)
    }

    @Test
    fun familyMealFallbackIncludesChildFriendlyKeywords() {
        val plan = RestaurantKeywordAiParser.fallbackPlan("带娃吃饭")

        assertTrue(plan.keywords.any { it.contains("亲子") })
        assertTrue(plan.keywords.any { it.contains("儿童") })
        assertTrue(plan.keywords.any { it.contains("商场") })
        assertTrue(plan.keywords.any { it.contains("家庭") })
        assertTrue(plan.keywords.any { it.contains("清淡") })
    }

    @Test
    fun lightRestaurantSearchDoesNotUseCookAvoidsOrCookTastes() {
        val plan = RestaurantKeywordAiParser.fallbackPlan("附近吃点清淡的")
        val allTerms = (plan.keywords + plan.mustMatch + plan.preferMatch + plan.negativeMatch).joinToString(" ")

        assertTrue(allTerms.contains("清淡"))
        assertFalse(allTerms.contains("香菜"))
        assertFalse(allTerms.contains("海鲜"))
        assertFalse(allTerms.contains("微辣"))
    }

    private fun assertEqualsNoJoinedKeyword(plan: RestaurantKeywordPlan) {
        assertFalse(plan.keywords.any { keyword ->
            keyword.contains("日料 自助") ||
                keyword.contains("日本料理 日式放题") ||
                keyword.split(Regex("\\s+")).size > 2
        })
    }
}
