package com.dinnerplan.chidian

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WanweiRecipeMapperTest {
    @Test
    fun mapsWanweiRecipeFieldsToSharedRecipeDto() {
        val recipe = WanweiRecipeMapper.toRecipeDto(
            WanweiRecipeItem(
                id = "5c3dfddfe9b6cc65afc9427e",
                cpName = "黄金鸡蛋盅",
                des = "适合早餐的鸡蛋菜。",
                type = "家常菜",
                typeV1 = "蛋类",
                typeV2 = "蒸菜",
                typeV3 = "早餐",
                largeImg = "https://img.example/large.jpg",
                smallImg = "https://img.example/small.jpg",
                yl = listOf(
                    WanweiIngredient("鸡蛋", "2个"),
                    WanweiIngredient("胡萝卜", "半根")
                ),
                steps = listOf(
                    WanweiStep(orderNum = "2", content = "上锅蒸熟", imgUrl = ""),
                    WanweiStep(orderNum = "1", content = "处理鸡蛋", imgUrl = "")
                ),
                tip = "火不要太大。"
            )
        )

        assertEquals("showapi_5c3dfddfe9b6cc65afc9427e", recipe.id)
        assertEquals("黄金鸡蛋盅", recipe.name)
        assertEquals("万维易源", recipe.source)
        assertEquals("https://img.example/large.jpg", recipe.coverUrl)
        assertEquals(listOf("处理鸡蛋", "上锅蒸熟"), recipe.steps)
        assertEquals("鸡蛋", recipe.ingredients.first().name)
        assertTrue(recipe.tags.contains("蒸菜"))
        assertTrue((recipe.ratingStars ?: 0.0) >= 4.0)
    }

    @Test
    fun ranksCompleteRelevantRecipesBeforeSparseOnes() {
        val complete = WanweiRecipeMapper.toRecipeDto(
            WanweiRecipeItem(
                id = "good",
                cpName = "粉蒸排骨",
                des = "经典粉蒸排骨。",
                type = "家常菜",
                typeV1 = "肉类",
                typeV2 = "蒸菜",
                typeV3 = "晚餐",
                largeImg = "https://img.example/good.jpg",
                yl = listOf(
                    WanweiIngredient("排骨", "500克"),
                    WanweiIngredient("蒸肉粉", "100克"),
                    WanweiIngredient("土豆", "1个")
                ),
                steps = listOf(
                    WanweiStep("1", "腌制排骨", ""),
                    WanweiStep("2", "裹蒸肉粉", ""),
                    WanweiStep("3", "上锅蒸熟", "")
                )
            )
        )
        val sparse = WanweiRecipeMapper.toRecipeDto(
            WanweiRecipeItem(
                id = "sparse",
                cpName = "排骨",
                yl = listOf(WanweiIngredient("排骨", "")),
                steps = listOf(WanweiStep("1", "煮熟", ""))
            )
        )

        val ranked = WanweiRecipeMapper.rankRecipes(listOf(sparse, complete), "粉蒸排骨")

        assertEquals(listOf("粉蒸排骨", "排骨"), ranked.map { it.name })
        assertTrue((ranked.first().ratingStars ?: 0.0) > (ranked.last().ratingStars ?: 0.0))
    }

    @Test
    fun parsesWanweiPageBeanContentListWithFlexibleFieldNames() {
        val items = WanweiRecipeApiClient().parseWanweiResponse(
            """
            {
              "showapi_res_code": 0,
              "showapi_res_body": {
                "ret_code": 0,
                "pageBean": {
                  "contentList": [
                    {
                      "cpId": "abc123",
                      "title": "番茄炒蛋",
                      "desc": "家常快手菜。",
                      "typeV1": "蛋类",
                      "typeV2": "家常菜",
                      "imgUrl": "https://img.example/tomato-egg.jpg",
                      "ingredients": [
                        { "materialName": "鸡蛋", "unit": "2个" },
                        { "materialName": "番茄", "unit": "2个" }
                      ],
                      "process": [
                        { "stepNum": "2", "desc": "下番茄炒出汁。", "pic": "" },
                        { "stepNum": "1", "desc": "先把鸡蛋炒熟盛出。", "pic": "" }
                      ],
                      "tips": "番茄先炒出汁更入味。"
                    }
                  ]
                }
              }
            }
            """.trimIndent()
        )

        val recipe = WanweiRecipeMapper.toRecipeDto(items.single())

        assertEquals("showapi_abc123", recipe.id)
        assertEquals("番茄炒蛋", recipe.name)
        assertEquals("https://img.example/tomato-egg.jpg", recipe.coverUrl)
        assertEquals(listOf("先把鸡蛋炒熟盛出。", "下番茄炒出汁。"), recipe.steps)
        assertEquals(listOf("鸡蛋", "番茄"), recipe.ingredients.map { it.name })
    }
}
