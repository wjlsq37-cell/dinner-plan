package com.dinnerplan.chidian

import com.dinnerplan.shared.CookSourceDto
import com.dinnerplan.shared.RecommendationModeDto
import kotlin.test.Test
import kotlin.test.assertEquals

class BackendProxyTest {
    @Test
    fun officialProxyUsesBackendPrefixWithoutDuplicatingApi() {
        assertEquals(
            "https://dinner-plan-pwa.vercel.app/api/backend/recommend/cook",
            backendEndpoint(DEFAULT_BACKEND_BASE_URL, "recommend/cook")
        )
    }

    @Test
    fun customBackendKeepsOriginalApiRoutes() {
        assertEquals(
            "http://10.0.2.2:8080/api/recommend/restaurant",
            backendEndpoint("http://10.0.2.2:8080/", "/recommend/restaurant")
        )
    }

    @Test
    fun legacyOfficialUrlsMigrateButCustomUrlsRemainUnchanged() {
        assertEquals(DEFAULT_BACKEND_BASE_URL, normalizeBackendBaseUrl(null))
        assertEquals(DEFAULT_BACKEND_BASE_URL, normalizeBackendBaseUrl("https://dinner-plan.vercel.app/"))
        assertEquals(DEFAULT_BACKEND_BASE_URL, normalizeBackendBaseUrl("https://dinner-plan-pwa.vercel.app"))
        assertEquals(
            "https://example.com/custom-backend",
            normalizeBackendBaseUrl(" https://example.com/custom-backend/ ")
        )
    }

    @Test
    fun unknownAiIntentAndSourceFallBackToTheRequest() {
        assertEquals(
            RecommendationModeDto.RECIPE_COMBO,
            normalizeRecommendationMode("用户想要一份清淡晚餐", RecommendationModeDto.RECIPE_COMBO)
        )
        assertEquals(
            CookSourceDto.AI_GENERATED,
            normalizeCookSource("unexpected-ai-source", CookSourceDto.AI_GENERATED)
        )
    }

    @Test
    fun canonicalAiIntentAndSourceStillDecodeNormally() {
        assertEquals(
            RecommendationModeDto.RECIPE_SINGLE,
            normalizeRecommendationMode("recipe_single", RecommendationModeDto.RECIPE_COMBO)
        )
        assertEquals(
            CookSourceDto.DATABASE,
            normalizeCookSource("database", CookSourceDto.AI_GENERATED)
        )
    }
}
