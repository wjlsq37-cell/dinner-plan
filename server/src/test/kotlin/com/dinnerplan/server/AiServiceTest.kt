package com.dinnerplan.server

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AiServiceTest {
    @Test
    fun aiIntentAcceptsScalarStringFields() {
        val intent = Json.decodeFromString(
            AiIntent.serializer(),
            """
            {
              "summary": "ok",
              "keywords": ["combo"],
              "taste": "\u5fae\u8fa3",
              "avoid": "",
              "intent": "recipe_combo"
            }
            """.trimIndent()
        )

        assertEquals(listOf("combo"), intent.keywords)
        assertEquals(listOf("\u5fae\u8fa3"), intent.taste)
        assertTrue(intent.avoid.isEmpty())
    }

    @Test
    fun aiIntentSplitsDelimitedScalarFields() {
        val intent = Json.decodeFromString(
            AiIntent.serializer(),
            """
            {
              "summary": "ok",
              "keywords": "\u4e24\u8364\u4e00\u7d20, \u4e00\u6c64",
              "taste": ["\u5fae\u8fa3"],
              "avoid": "\u9999\u83dc/\u8471",
              "intent": "recipe_combo"
            }
            """.trimIndent()
        )

        assertEquals(listOf("\u4e24\u8364\u4e00\u7d20", "\u4e00\u6c64"), intent.keywords)
        assertEquals(listOf("\u5fae\u8fa3"), intent.taste)
        assertEquals(listOf("\u9999\u83dc", "\u8471"), intent.avoid)
    }

    @Test
    fun aiClientTimeoutsAllowSlowRecipeGeneration() {
        assertTrue(AI_CONNECT_TIMEOUT_MILLIS >= 20_000)
        assertTrue(AI_REQUEST_TIMEOUT_MILLIS >= 120_000)
        assertTrue(AI_SOCKET_TIMEOUT_MILLIS >= 120_000)
    }

    @Test
    fun aiCookGenerationAcceptsNumericIdsFromModelOutput() {
        val generation = parseAiCookGeneration(
            Json { ignoreUnknownKeys = true },
            """
            {
              "intent": "RECIPE_COMBO",
              "summary": "微辣家常组合",
              "mealPlans": [
                {
                  "id": 1,
                  "title": "微辣家常晚餐组合",
                  "structure": "两荤一素 · 一汤 · 主食",
                  "cookTime": "约 50 分钟",
                  "servings": "2-3 人份",
                  "dishes": [
                    {"course": "荤菜", "name": "青椒鸡丁", "note": "微辣", "badge": "Meat", "recipeId": 2}
                  ],
                  "shoppingList": ["鸡肉", "青椒"],
                  "timeline": ["先备菜", "再炒菜"]
                }
              ],
              "recipes": [
                {
                  "id": 2,
                  "name": "青椒鸡丁",
                  "ingredients": [{"name": "鸡肉", "amount": "250g"}],
                  "steps": ["切丁", "炒熟"]
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals("1", generation?.mealPlans?.first()?.id)
        assertEquals("2", generation?.mealPlans?.first()?.dishes?.first()?.recipeId)
        assertEquals("2", generation?.recipes?.first()?.id)
        assertEquals("青椒鸡丁", generation?.recipes?.first()?.name)
    }
}
