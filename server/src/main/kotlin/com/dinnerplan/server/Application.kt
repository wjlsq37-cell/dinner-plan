package com.dinnerplan.server

import com.dinnerplan.shared.CancelRecommendationRequest
import com.dinnerplan.shared.CancelRecommendationResponse
import com.dinnerplan.shared.HealthResponse
import com.dinnerplan.shared.RecommendationModeDto
import com.dinnerplan.shared.RecommendationRequest
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.job
import kotlinx.serialization.json.Json
import java.nio.file.Paths
import kotlin.coroutines.coroutineContext

fun main() {
    val config = AppConfig.load()
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        module(config)
    }.start(wait = true)
}

fun Application.module(config: AppConfig = AppConfig.load()) {
    val jsonConfig = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }
    val seedRepository = SeedRepository()
    val aiService = AiService(config, jsonConfig)
    val amapService = AmapService(config, jsonConfig)
    val recipeCorpusRepository = RecipeCorpusRepository(Paths.get(config.recipeCorpusDbPath), jsonConfig)
    val recommendationService = RecommendationService(seedRepository, aiService, amapService, recipeCorpusRepository)
    val recommendationTaskRegistry = RecommendationTaskRegistry()

    install(ContentNegotiation) {
        json(jsonConfig)
    }
    install(StatusPages) {
        exception<CancellationException> { _, cause ->
            throw cause
        }
        exception<Throwable> { call, cause ->
            println("Request failed: ${cause.message ?: cause::class.simpleName}")
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf(
                    "error" to "backend_error",
                    "message" to (cause.message ?: "后端内部错误")
                )
            )
        }
    }

    routing {
        get("/health") {
            val recipeStatus = recipeCorpusRepository.status()
            call.respond(
                HealthResponse(
                    ok = config.missingRequired().isEmpty(),
                    missingConfig = config.missingRequired(),
                    aiConfigured = config.aiConfigured,
                    amapConfigured = config.amapConfigured,
                    recipeCorpusReady = recipeStatus.ready,
                    recipeCorpusCount = recipeStatus.count,
                    recipeCorpusPath = recipeStatus.path
                )
            )
        }

        post("/api/recommend/cook") {
            val request = call.receive<RecommendationRequest>()
            if (request.query.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "query is required"))
                return@post
            }
            val requestId = request.requestId?.trim()?.takeIf { it.isNotEmpty() }
            if (requestId != null) {
                recommendationTaskRegistry.register(requestId, coroutineContext.job)
            }
            try {
                call.respond(recommendationService.recommendCook(request))
            } finally {
                if (requestId != null) {
                    recommendationTaskRegistry.complete(requestId)
                }
            }
        }

        post("/api/recommend/cook/cancel") {
            val request = call.receive<CancelRecommendationRequest>()
            val requestId = request.requestId.trim()
            if (requestId.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "requestId is required"))
                return@post
            }
            val cancelled = recommendationTaskRegistry.cancel(requestId)
            call.respond(
                CancelRecommendationResponse(
                    requestId = requestId,
                    cancelled = cancelled,
                    message = if (cancelled) {
                        "已取消本次 AI 菜谱制作"
                    } else {
                        "未找到正在制作的 AI 菜谱任务"
                    }
                )
            )
        }

        post("/api/recommend/restaurant") {
            val request = call.receive<RecommendationRequest>()
            if (request.query.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "query is required"))
                return@post
            }
            val restaurantRequest = request.copy(mode = RecommendationModeDto.RESTAURANT)
            call.respond(recommendationService.recommendRestaurants(restaurantRequest))
        }

        get("/api/recipes/{id}") {
            val recipeId = call.parameters["id"]
            val recipe = recipeId?.let(recipeCorpusRepository::recipeById)
                ?: recipeId?.let(seedRepository::recipeById)
            if (recipe == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "recipe not found"))
            } else {
                call.respond(recipe)
            }
        }

        get("/api/meal-plans/{id}") {
            val mealPlan = call.parameters["id"]?.let(seedRepository::mealPlanById)
            if (mealPlan == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "meal plan not found"))
            } else {
                call.respond(mealPlan)
            }
        }
    }
}
