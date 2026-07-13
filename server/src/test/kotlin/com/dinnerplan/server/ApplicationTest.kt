package com.dinnerplan.server

import com.dinnerplan.shared.CookRecommendationResponse
import com.dinnerplan.shared.CookSourceDto
import com.dinnerplan.shared.CancelRecommendationRequest
import com.dinnerplan.shared.CancelRecommendationResponse
import com.dinnerplan.shared.HealthResponse
import com.dinnerplan.shared.RecommendationModeDto
import com.dinnerplan.shared.RecommendationRequest
import com.dinnerplan.shared.RestaurantRecommendationResponse
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.Job
import kotlinx.serialization.json.Json
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ApplicationTest {
    private val noSecretConfig = AppConfig(
        aiBaseUrl = "https://api.openai.com/v1",
        aiApiKey = "",
        aiModel = "gpt-4o-mini",
        amapWebKey = "",
        port = 8080
    )

    @Test
    fun healthReportsMissingConfig() = testApplication {
        application { module(noSecretConfig) }
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        val health = response.body<HealthResponse>()
        assertFalse(health.ok)
        assertFalse(health.aiConfigured)
        assertFalse(health.amapConfigured)
        assertTrue(health.missingConfig.isNotEmpty())
        assertFalse(health.recipeCorpusReady)
        assertEquals(0, health.recipeCorpusCount)
    }

    @Test
    fun healthReportsRecipeCorpusStatus() = testApplication {
        val db = importedCorpus(
            """{"name":"粉蒸排骨","dish":"粉蒸排骨","description":"经典蒸菜。","recipeIngredient":["排骨","蒸肉米粉"],"recipeInstructions":["腌制排骨","拌粉蒸熟"],"author":"me","keywords":["粉蒸排骨","川菜"]}"""
        )
        application { module(noSecretConfig.copy(recipeCorpusDbPath = db.toString())) }
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val response = client.get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
        val health = response.body<HealthResponse>()
        assertTrue(health.recipeCorpusReady)
        assertEquals(1, health.recipeCorpusCount)
        assertTrue(health.recipeCorpusPath.endsWith("recipes.sqlite"))
    }

    @Test
    fun databaseCookRecommendationSearchesCorpusWithoutAiKey() = testApplication {
        val db = importedCorpus(
            """{"name":"粉蒸排骨","dish":"粉蒸排骨","description":"经典川味蒸菜。","recipeIngredient":["500g排骨","蒸肉米粉","红薯"],"recipeInstructions":["排骨腌制入味","拌入蒸肉米粉","铺红薯上锅蒸熟"],"author":"me","keywords":["粉蒸排骨","川菜","家常菜"]}"""
        )
        application { module(noSecretConfig.copy(recipeCorpusDbPath = db.toString())) }
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val response = client.post("/api/recommend/cook") {
            contentType(ContentType.Application.Json)
            setBody(
                RecommendationRequest(
                    query = "粉蒸排骨",
                    mode = RecommendationModeDto.RECIPE_SINGLE,
                    cookSource = CookSourceDto.DATABASE
                )
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<CookRecommendationResponse>()
        assertEquals(CookSourceDto.DATABASE, body.source)
        assertEquals(1, body.totalMatches)
        assertEquals("粉蒸排骨", body.recipes.first().name)
        assertTrue(body.recipes.first().ratingStars ?: 0.0 >= 4.0)
        assertEquals(null, body.fallbackReason)
    }

    @Test
    fun broadDatabaseCookRecommendationSamplesCorpusWithoutDinnerKeyword() = testApplication {
        val db = importedCorpus(
            """
            {"name":"葱油鸡","dish":"家常菜","description":"鲜香冷盘。","recipeIngredient":["鸡腿","小葱"],"recipeInstructions":["鸡腿煮熟","淋葱油"],"author":"me","keywords":["鸡肉","家常菜"]}
            {"name":"香煎豆腐","dish":"家常菜","description":"外焦里嫩。","recipeIngredient":["豆腐","鸡蛋"],"recipeInstructions":["豆腐裹蛋液","煎至金黄"],"author":"me","keywords":["豆腐","素菜"]}
            {"name":"鲜虾丝瓜汤","dish":"汤羹","description":"清爽汤品。","recipeIngredient":["虾","丝瓜"],"recipeInstructions":["虾仁处理","丝瓜煮汤"],"author":"me","keywords":["海鲜","汤"]}
            """.trimIndent()
        )
        application { module(noSecretConfig.copy(recipeCorpusDbPath = db.toString())) }
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val response = client.post("/api/recommend/cook") {
            contentType(ContentType.Application.Json)
            setBody(
                RecommendationRequest(
                    query = "",
                    mode = RecommendationModeDto.RECIPE_SINGLE,
                    cookSource = CookSourceDto.DATABASE,
                    preferences = com.dinnerplan.shared.UserPreferenceDto(avoids = listOf("海鲜")),
                    broadSearch = true
                )
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<CookRecommendationResponse>()
        assertTrue(body.recipes.map { it.name }.containsAll(listOf("葱油鸡", "香煎豆腐")))
        assertFalse(body.recipes.any { it.name == "鲜虾丝瓜汤" })
        assertTrue(body.summary.contains("随机"))
    }

    @Test
    fun broadDatabaseCookRecommendationDoesNotReturnSeedRecipesWhenCorpusIsMissing() = testApplication {
        val missingDb = createTempDirectory("missing-recipe-corpus").resolve("recipes.sqlite")
        application { module(noSecretConfig.copy(recipeCorpusDbPath = missingDb.toString())) }
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val response = client.post("/api/recommend/cook") {
            contentType(ContentType.Application.Json)
            setBody(
                RecommendationRequest(
                    query = "",
                    mode = RecommendationModeDto.RECIPE_SINGLE,
                    cookSource = CookSourceDto.DATABASE,
                    broadSearch = true
                )
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<CookRecommendationResponse>()
        assertTrue(body.recipes.isEmpty())
        assertTrue(body.mealPlans.isEmpty())
        assertEquals(0, body.totalMatches)
        assertNotNull(body.fallbackReason)
    }

    @Test
    fun aiGeneratedCookRecommendationFallsBackClearlyWhenAiUnavailable() = testApplication {
        val db = importedCorpus(
            """{"name":"番茄炒鸡蛋","dish":"番茄炒鸡蛋","description":"家常下饭菜。","recipeIngredient":["鸡蛋","番茄"],"recipeInstructions":["炒鸡蛋","炒番茄","合炒调味"],"author":"me","keywords":["鸡蛋","家常菜"]}"""
        )
        application { module(noSecretConfig.copy(recipeCorpusDbPath = db.toString())) }
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val response = client.post("/api/recommend/cook") {
            contentType(ContentType.Application.Json)
            setBody(
                RecommendationRequest(
                    query = "番茄炒鸡蛋",
                    mode = RecommendationModeDto.RECIPE_SINGLE,
                    cookSource = CookSourceDto.AI_GENERATED
                )
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<CookRecommendationResponse>()
        assertEquals(CookSourceDto.AI_GENERATED, body.source)
        assertTrue(body.recipes.isNotEmpty())
        assertNotNull(body.fallbackReason)
        assertTrue(body.fallbackReason!!.contains("AI"))
    }

    @Test
    fun cookRecommendationFallsBackToSeedDataWithoutAiKey() = testApplication {
        application { module(noSecretConfig) }
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val response = client.post("/api/recommend/cook") {
            contentType(ContentType.Application.Json)
            setBody(RecommendationRequest("两荤一素、一汤、主食、微辣", RecommendationModeDto.RECIPE_COMBO))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<CookRecommendationResponse>()
        assertTrue(body.mealPlans.isNotEmpty())
        assertTrue(body.recipes.isNotEmpty())
        assertNotNull(body.fallbackReason)
    }

    @Test
    fun cookRecommendationAdaptsMealPlanToQueryWhenAiUnavailable() = testApplication {
        application { module(noSecretConfig) }
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val response = client.post("/api/recommend/cook") {
            contentType(ContentType.Application.Json)
            setBody(RecommendationRequest("三荤一素、一汤、主食、不辣", RecommendationModeDto.RECIPE_COMBO))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<CookRecommendationResponse>()
        val firstPlan = body.mealPlans.first()
        assertTrue(firstPlan.title.contains("三荤一素"))
        assertTrue(firstPlan.tags.contains("不辣"))
        assertEquals(3, firstPlan.dishes.count { it.course == "荤菜" })
        assertEquals(1, firstPlan.dishes.count { it.course == "素菜" })
        assertTrue(firstPlan.dishes.none { it.name.contains("酸辣") || it.name.contains("微辣") })
    }

    @Test
    fun restaurantRecommendationDoesNotInventPoisWithoutAmapKey() = testApplication {
        application { module(noSecretConfig) }
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val response = client.post("/api/recommend/restaurant") {
            contentType(ContentType.Application.Json)
            setBody(RecommendationRequest("附近 5km 牛肉面", RecommendationModeDto.RESTAURANT))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<RestaurantRecommendationResponse>()
        assertTrue(body.restaurants.isEmpty())
        assertNotNull(body.fallbackReason)
    }

    @Test
    fun broadRestaurantRecommendationAllowsEmptyQuery() = testApplication {
        application { module(noSecretConfig) }
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val response = client.post("/api/recommend/restaurant") {
            contentType(ContentType.Application.Json)
            setBody(
                RecommendationRequest(
                    query = "",
                    mode = RecommendationModeDto.RESTAURANT,
                    broadSearch = true
                )
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<RestaurantRecommendationResponse>()
        assertTrue(body.restaurants.isEmpty())
        assertNotNull(body.fallbackReason)
    }

    @Test
    fun cancelCookRecommendationReturnsFalseForUnknownRequest() = testApplication {
        application { module(noSecretConfig) }
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val response = client.post("/api/recommend/cook/cancel") {
            contentType(ContentType.Application.Json)
            setBody(CancelRecommendationRequest("missing-request"))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<CancelRecommendationResponse>()
        assertEquals("missing-request", body.requestId)
        assertFalse(body.cancelled)
        assertTrue(body.message.contains("未找到"))
    }

    @Test
    fun recommendationTaskRegistryCancelsActiveJob() {
        val registry = RecommendationTaskRegistry()
        val job = Job()

        registry.register("cook-request-1", job)
        val cancelled = registry.cancel("cook-request-1")

        assertTrue(cancelled)
        assertFalse(job.isActive)
        assertFalse(registry.isActive("cook-request-1"))
    }

    private fun importedCorpus(jsonLine: String) = createTempDirectory("recipe-corpus-api-test").let { dir ->
        val source = dir.resolve("recipes.jsonl")
        val db = dir.resolve("recipes.sqlite")
        source.writeText(jsonLine + "\n", Charsets.UTF_8)
        RecipeCorpusImporter().importJsonl(source, db)
        db
    }
}
