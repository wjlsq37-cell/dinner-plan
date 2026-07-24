package com.dinnerplan.chidian

import com.dinnerplan.shared.CancelRecommendationRequest
import com.dinnerplan.shared.CancelRecommendationResponse
import com.dinnerplan.shared.CookSourceDto
import com.dinnerplan.shared.CookRecommendationResponse
import com.dinnerplan.shared.MealPlanDto
import com.dinnerplan.shared.RecipeDto
import com.dinnerplan.shared.RecommendationModeDto
import com.dinnerplan.shared.RecommendationRequest
import com.dinnerplan.shared.RestaurantRecommendationResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import okhttp3.Protocol
import java.io.IOException
import kotlinx.serialization.Serializable

class BackendApiClient {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    private val client = HttpClient(OkHttp) {
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 180_000
            socketTimeoutMillis = 180_000
        }
        install(ContentNegotiation) {
            json(json)
        }
        engine {
            config {
                retryOnConnectionFailure(true)
                protocols(listOf(Protocol.HTTP_1_1))
            }
        }
    }

    suspend fun recommendCook(baseUrl: String, request: RecommendationRequest): CookRecommendationResponse {
        val response = execute<BackendCookRecommendationResponse> {
            client.post(backendEndpoint(baseUrl, "recommend/cook")) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }
        return response.toDomain(request)
    }

    suspend fun cancelCookRecommendation(baseUrl: String, request: CancelRecommendationRequest): CancelRecommendationResponse {
        return execute {
            client.post(backendEndpoint(baseUrl, "recommend/cook/cancel")) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }
    }

    suspend fun recommendRestaurants(baseUrl: String, request: RecommendationRequest): RestaurantRecommendationResponse {
        return execute {
            client.post(backendEndpoint(baseUrl, "recommend/restaurant")) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }
    }

    suspend fun recipe(baseUrl: String, id: String): RecipeDto {
        return execute {
            client.get(backendEndpoint(baseUrl, "recipes/$id"))
        }
    }

    suspend fun mealPlan(baseUrl: String, id: String): MealPlanDto {
        return execute {
            client.get(backendEndpoint(baseUrl, "meal-plans/$id"))
        }
    }

    private suspend inline fun <reified T> execute(noinline request: suspend () -> HttpResponse): T {
        var lastIoError: IOException? = null
        repeat(2) { attempt ->
            try {
                val response = request()
                if (!response.status.isSuccess()) {
                    val detail = response.bodyAsText().ifBlank { response.status.description }
                    throw IllegalStateException("后端返回 ${response.status.value}：${detail.take(240)}")
                }
                return response.body()
            } catch (error: IOException) {
                lastIoError = error
                if (attempt == 1) {
                    throw IOException("服务暂时不可用，请稍后再试。", error)
                }
            }
        }
        throw lastIoError ?: IOException("服务暂时不可用，请稍后再试。")
    }
}

internal fun backendEndpoint(baseUrl: String, operation: String): String {
    val normalizedBase = baseUrl.trim().trimEnd('/')
    val normalizedOperation = operation.trim().trim('/')
    val apiPrefix = if (normalizedBase.endsWith("/api/backend", ignoreCase = true)) "" else "/api"
    return "$normalizedBase$apiPrefix/$normalizedOperation"
}

@Serializable
private data class BackendCookRecommendationResponse(
    val intent: String? = null,
    val summary: String = "",
    val mealPlans: List<MealPlanDto> = emptyList(),
    val recipes: List<RecipeDto> = emptyList(),
    val fallbackReason: String? = null,
    val source: String? = null,
    val totalMatches: Int? = null
) {
    fun toDomain(request: RecommendationRequest): CookRecommendationResponse {
        return CookRecommendationResponse(
            intent = normalizeRecommendationMode(intent, request.mode),
            summary = summary,
            mealPlans = mealPlans,
            recipes = recipes,
            fallbackReason = fallbackReason,
            source = normalizeCookSource(source, request.cookSource),
            totalMatches = totalMatches ?: (mealPlans.size + recipes.size)
        )
    }
}

internal fun normalizeRecommendationMode(value: String?, fallback: RecommendationModeDto): RecommendationModeDto {
    return value?.trim()?.uppercase()?.let { normalized ->
        RecommendationModeDto.entries.firstOrNull { it.name == normalized }
    } ?: fallback
}

internal fun normalizeCookSource(value: String?, fallback: CookSourceDto): CookSourceDto {
    return value?.trim()?.uppercase()?.let { normalized ->
        CookSourceDto.entries.firstOrNull { it.name == normalized }
    } ?: fallback
}
