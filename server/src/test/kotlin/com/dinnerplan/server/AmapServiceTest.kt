package com.dinnerplan.server

import com.dinnerplan.shared.RestaurantSearchSpec
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AmapServiceTest {
    @Test
    fun japaneseCuisineAliasesUseStrictSearchSpec() {
        listOf("日料", "日本料理", "寿司", "居酒屋").forEach { query ->
            val spec = RestaurantSearchSpec.fromQuery(query)

            assertTrue(spec.primaryKeywords.contains("日料"))
            assertTrue(spec.primaryKeywords.contains("日本料理"))
            assertTrue(spec.primaryKeywords.contains("寿司"))
            assertTrue(spec.strictMatchTerms.contains("日料"))
            assertTrue(spec.strictMatchTerms.contains("日本料理"))
            assertTrue(spec.useGenericFallback)
            assertNotNull(spec.genericFallbackReason)
        }
    }

    @Test
    fun japaneseCuisineFilterRemovesGenericRestaurants() {
        val spec = RestaurantSearchSpec.fromQuery("日料")

        val restaurants = filterAndRankRestaurantPois(
            pois = listOf(
                poi(id = "1", name = "肯德基(市民中心店)", type = "餐饮服务;快餐厅;肯德基", distance = "80"),
                poi(id = "2", name = "兰州牛肉面", type = "餐饮服务;中餐厅", distance = "90"),
                poi(id = "3", name = "鸟贵族日式烧鸟", type = "餐饮服务;外国餐厅;日本料理", distance = "150"),
                poi(id = "4", name = "隐泉寿司", type = "餐饮服务;外国餐厅;日本料理", distance = "300")
            ),
            spec = spec,
            query = "日料"
        )

        assertEquals(listOf("鸟贵族日式烧鸟", "隐泉寿司"), restaurants.map { it.name })
    }

    @Test
    fun japaneseCuisineFallbackReasonIsExplicit() {
        val spec = RestaurantSearchSpec.fromQuery("附近日料")

        assertEquals("附近未找到日料相关店铺，以下为附近餐厅补位。", spec.genericFallbackReason)
    }

    @Test
    fun cuisineKeywordSearchUsesGenericFallbackWhenNoMatch() {
        val spec = RestaurantSearchSpec.fromQuery("5km 内，微辣，人均 50 内，适合晚饭")

        assertEquals(listOf("湘菜"), spec.primaryKeywords)
        assertTrue(spec.useGenericFallback)
        assertEquals("附近未找到湘菜相关店铺，以下为附近餐厅补位。", spec.genericFallbackReason)
    }

    private fun poi(
        id: String,
        name: String,
        type: String,
        distance: String
    ) = buildJsonObject {
        put("id", id)
        put("name", name)
        put("type", type)
        put("address", "测试地址")
        put("distance", distance)
        put("location", "120.205,30.256")
        putJsonObject("biz_ext") {
            put("rating", "4.5")
            put("cost", "80")
        }
        put("photos", buildJsonArray {})
    }
}
