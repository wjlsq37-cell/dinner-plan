package com.dinnerplan.server

import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RecipeCorpusRepositoryTest {
    @Test
    fun importerReadsJsonLinesSkipsInvalidRowsAndComputesStars() {
        val dir = createTempDirectory("recipe-corpus-test")
        val source = dir.resolve("recipes.jsonl")
        val db = dir.resolve("recipes.sqlite")
        source.writeText(
            """
            {"name":"粉蒸排骨","dish":"粉蒸排骨","description":"经典川味蒸菜。","recipeIngredient":["500g排骨","蒸肉米粉","红薯"],"recipeInstructions":["排骨腌制入味","拌入蒸肉米粉","铺红薯上锅蒸熟"],"author":"me","keywords":["粉蒸排骨","川菜","家常菜"]}
            not-json
            {"name":"空菜谱","dish":"Unknown","description":"","recipeIngredient":[],"recipeInstructions":[],"author":"me","keywords":[]}
            """.trimIndent() + "\n",
            Charsets.UTF_8
        )

        val result = RecipeCorpusImporter().importJsonl(source, db)
        val repository = RecipeCorpusRepository(db)
        val search = repository.search("粉蒸排骨", limit = 10)

        assertEquals(2, result.imported)
        assertEquals(1, result.skipped)
        assertEquals(2, repository.status().count)
        assertEquals("粉蒸排骨", search.recipes.first().name)
        assertTrue(search.recipes.first().ratingStars ?: 0.0 >= 4.0)
        assertEquals("recipe_corpus", search.recipes.first().source)
    }

    @Test
    fun databaseSearchOrdersRelevantRowsByStarsFirst() {
        val dir = createTempDirectory("recipe-corpus-ranking-test")
        val source = dir.resolve("recipes.jsonl")
        val db = dir.resolve("recipes.sqlite")
        source.writeText(
            """
            {"name":"鸡蛋快手菜","dish":"鸡蛋","description":"","recipeIngredient":["鸡蛋"],"recipeInstructions":["炒熟"],"author":"me","keywords":["鸡蛋"]}
            {"name":"番茄炒鸡蛋","dish":"番茄炒鸡蛋","description":"家常下饭菜，步骤完整。","recipeIngredient":["鸡蛋","番茄","葱花","盐","白糖"],"recipeInstructions":["鸡蛋打散炒至凝固","番茄炒出汁","回锅合炒并调味","撒葱花出锅"],"author":"me","keywords":["鸡蛋","番茄","家常菜","快手菜"]}
            """.trimIndent() + "\n",
            Charsets.UTF_8
        )

        RecipeCorpusImporter().importJsonl(source, db)
        val search = RecipeCorpusRepository(db).search("鸡蛋", limit = 10)

        assertEquals("番茄炒鸡蛋", search.recipes.first().name)
        assertTrue((search.recipes[0].ratingStars ?: 0.0) >= (search.recipes[1].ratingStars ?: 0.0))
    }

    @Test
    fun databaseSearchSplitsSpacesAndPunctuationIntoUsefulTerms() {
        val dir = createTempDirectory("recipe-corpus-term-test")
        val source = dir.resolve("recipes.jsonl")
        val db = dir.resolve("recipes.sqlite")
        source.writeText(
            """
            {"name":"番茄炒鸡蛋","dish":"家常菜","description":"晚餐快手下饭菜。","recipeIngredient":["鸡蛋","番茄","葱花"],"recipeInstructions":["鸡蛋炒熟","番茄炒出汁","合炒调味"],"author":"me","keywords":["番茄","鸡蛋","晚餐"]}
            {"name":"清炒西兰花","dish":"素菜","description":"清淡蔬菜。","recipeIngredient":["西兰花"],"recipeInstructions":["焯水","清炒"],"author":"me","keywords":["清淡"]}
            """.trimIndent() + "\n",
            Charsets.UTF_8
        )

        RecipeCorpusImporter().importJsonl(source, db)
        val repository = RecipeCorpusRepository(db)

        assertEquals("番茄炒鸡蛋", repository.search("番茄 鸡蛋", limit = 10).recipes.first().name)
        assertEquals("番茄炒鸡蛋", repository.search("番茄、鸡蛋/晚餐", limit = 10).recipes.first().name)
    }

    @Test
    fun databaseSearchCompactsFullWidthSymbolsBeforeMatching() {
        val dir = createTempDirectory("recipe-corpus-compact-test")
        val source = dir.resolve("recipes.jsonl")
        val db = dir.resolve("recipes.sqlite")
        source.writeText(
            """
            {"name":"abcdef","dish":"test","description":"compact target","recipeIngredient":["abcdef"],"recipeInstructions":["cook"],"author":"me","keywords":["abcdef"]}
            {"name":"zzzzzz","dish":"test","description":"higher quality distractor","recipeIngredient":["many","complete","items"],"recipeInstructions":["one","two","three","four"],"author":"me","keywords":["other"]}
            """.trimIndent() + "\n",
            Charsets.UTF_8
        )

        RecipeCorpusImporter().importJsonl(source, db)
        val result = RecipeCorpusRepository(db).search("abc\uFF0Fdef", limit = 10)

        assertTrue(result.recipes.isNotEmpty())
        assertEquals("abcdef", result.recipes.first().name)
    }

    @Test
    fun databaseSearchWithOnlySymbolsFallsBackToTopRatedRecipes() {
        val dir = createTempDirectory("recipe-corpus-symbol-test")
        val source = dir.resolve("recipes.jsonl")
        val db = dir.resolve("recipes.sqlite")
        source.writeText(
            """
            {"name":"番茄炒鸡蛋","dish":"家常菜","description":"晚餐快手下饭菜。","recipeIngredient":["鸡蛋","番茄","葱花"],"recipeInstructions":["鸡蛋炒熟","番茄炒出汁","合炒调味"],"author":"me","keywords":["番茄","鸡蛋","晚餐"]}
            """.trimIndent() + "\n",
            Charsets.UTF_8
        )

        RecipeCorpusImporter().importJsonl(source, db)
        val search = RecipeCorpusRepository(db).search("  、 / ； ： （）  ", limit = 10)

        assertTrue(search.recipes.isNotEmpty())
        assertEquals("番茄炒鸡蛋", search.recipes.first().name)
    }

    @Test
    fun recipeByIdReturnsFullDatabaseRecipe() {
        val dir = createTempDirectory("recipe-corpus-detail-test")
        val source = dir.resolve("recipes.jsonl")
        val db = dir.resolve("recipes.sqlite")
        source.writeText(
            """{"name":"低糖拿铁冰沙","dish":"冰沙","description":"低糖饮品。","recipeIngredient":["咖啡冰块","牛奶"],"recipeInstructions":["咖啡冻成冰块","与牛奶打成冰沙"],"author":"me","keywords":["咖啡","饮品"]}""" + "\n",
            Charsets.UTF_8
        )

        RecipeCorpusImporter().importJsonl(source, db)
        val repository = RecipeCorpusRepository(db)
        val recipe = repository.search("拿铁冰沙", limit = 1).recipes.first()
        val detail = repository.recipeById(recipe.id)

        assertNotNull(detail)
        assertEquals("低糖拿铁冰沙", detail.name)
        assertEquals(listOf("咖啡冰块", "牛奶"), detail.ingredients.map { it.name })
        assertEquals("recipe_corpus", detail.source)
    }
}
