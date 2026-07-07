package com.dinnerplan.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AmapMultiKeywordSearchTest {
    @Test
    fun searchesEveryKeywordAndDoesNotStopOnEmptyResult() = kotlinx.coroutines.test.runTest {
        val calls = mutableListOf<String>()
        val search = AmapMultiKeywordSearch { keyword ->
            calls += keyword
            when (keyword) {
                "日料自助" -> listOf(candidate("poi_1", "赤坂亭日料自助", "银泰城"))
                "日本料理" -> emptyList()
                "寿司" -> listOf(candidate("poi_2", "隐泉寿司", "湖滨"))
                else -> emptyList()
            }
        }

        val result = search.search(
            RestaurantKeywordPlan(
                summary = "日料自助",
                keywords = listOf("日料自助", "日本料理", "寿司")
            )
        )

        assertEquals(listOf("日料自助", "日本料理", "寿司"), calls)
        assertEquals(listOf("poi_1", "poi_2"), result.map { it.id })
    }

    @Test
    fun deduplicatesByIdThenNameAndAddress() = kotlinx.coroutines.test.runTest {
        val search = AmapMultiKeywordSearch { keyword ->
            when (keyword) {
                "日料自助" -> listOf(
                    candidate("poi_1", "赤坂亭日料自助", "银泰城"),
                    candidate("", "隐泉寿司", "湖滨路 1 号")
                )
                "寿司" -> listOf(
                    candidate("poi_1", "赤坂亭日料自助", "银泰城"),
                    candidate("", "隐泉寿司", "湖滨路 1 号")
                )
                else -> emptyList()
            }
        }

        val result = search.search(RestaurantKeywordPlan(keywords = listOf("日料自助", "寿司")))

        assertEquals(2, result.size)
        assertEquals(listOf("poi_1", ""), result.map { it.id })
        assertTrue(result.any { it.name == "隐泉寿司" })
    }

    private fun candidate(id: String, name: String, address: String): RestaurantCandidate {
        return RestaurantCandidate(
            id = id,
            name = name,
            type = "餐饮服务;外国餐厅;日本料理",
            address = address,
            distance = "300m",
            rating = "4.6",
            cost = "120",
            businessArea = "湖滨",
            tags = listOf("日本料理"),
            description = "",
            photoTitles = emptyList(),
            photoUrl = "",
            latitude = 30.0,
            longitude = 120.0
        )
    }
}
