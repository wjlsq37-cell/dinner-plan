package com.dinnerplan.chidian

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class LocalPersistenceAndRecipeDetailTest {
    @Test
    fun savedItemsPersistFullObjectsInDedicatedLocalCaches() {
        val mainActivity = readProjectFile("app/src/main/java/com/dinnerplan/chidian/MainActivity.kt")
        val savedScreen = readProjectFile("app/src/main/java/com/dinnerplan/chidian/ui/screens/SavedSettingsScreens.kt")

        assertTrue("SavedMealPlanCache" in mainActivity)
        assertTrue("SavedRecipeCache" in mainActivity)
        assertTrue("SavedRestaurantCache" in mainActivity)
        assertTrue("savedMealPlanCache" in mainActivity)
        assertTrue("savedRecipeCache" in mainActivity)
        assertTrue("savedRestaurantCache" in mainActivity)
        assertTrue("decodeMealPlanCache(preferences[SettingsKeys.SavedMealPlanCache])" in mainActivity)
        assertTrue("decodeRecipeCache(preferences[SettingsKeys.SavedRecipeCache])" in mainActivity)
        assertTrue("decodeRestaurantCache(preferences[SettingsKeys.SavedRestaurantCache])" in mainActivity)
        assertTrue("preferences[SettingsKeys.SavedMealPlanCache]" in mainActivity)
        assertTrue("preferences[SettingsKeys.SavedRecipeCache]" in mainActivity)
        assertTrue("preferences[SettingsKeys.SavedRestaurantCache]" in mainActivity)
        assertTrue("state.savedRecipeCache" in savedScreen)
        assertTrue("state.savedRestaurantCache" in savedScreen)
    }

    @Test
    fun recipeDetailDisplaysStepImagesWhenPresent() {
        val sharedModels = readProjectFile("shared/src/main/kotlin/com/dinnerplan/shared/ApiModels.kt")
        val mainActivity = readProjectFile("app/src/main/java/com/dinnerplan/chidian/MainActivity.kt")
        val detailScreen = readProjectFile("app/src/main/java/com/dinnerplan/chidian/ui/screens/DetailScreens.kt")

        assertTrue("stepImageUrls" in sharedModels)
        assertTrue("val stepImageUrls: List<String> = emptyList()" in mainActivity)
        assertTrue("stepImageUrls = stepImageUrls" in mainActivity)
        assertTrue("DetailStepList(recipe.steps, recipe.stepImageUrls)" in detailScreen)
        assertTrue("AsyncImage(" in detailScreen.substringAfter("private fun DetailStepList("))
    }

    @Test
    fun mxnzpDetailImagesLoadOnlyWhenRecipeDetailOpens() {
        val mainActivity = readProjectFile("app/src/main/java/com/dinnerplan/chidian/MainActivity.kt")
        val recipeClient = readProjectFile("app/src/main/java/com/dinnerplan/chidian/WanweiRecipeApiClient.kt")
        val recipeDetailBranch = mainActivity.substringAfter("is Screen.RecipeDetail ->")
            .substringBefore("Screen.NearbyRestaurant")

        assertTrue("val recipeApiClient = remember { WanweiRecipeApiClient() }" in mainActivity)
        assertTrue("fun loadRecipeDetailIfNeeded(id: String)" in mainActivity)
        assertTrue("LaunchedEffect(screen.id)" in recipeDetailBranch)
        assertTrue("loadRecipeDetailIfNeeded(screen.id)" in recipeDetailBranch)
        assertTrue("suspend fun loadMxnzpRecipeDetail" in recipeClient)
        assertTrue("mergeMxnzpDetailInto(recipe)" in recipeClient)
    }

    private fun readProjectFile(relativePath: String): String {
        val candidates = listOf(Path.of(relativePath), Path.of("..").resolve(relativePath))
        val path = candidates.firstOrNull { it.toFile().exists() }
            ?: error("Cannot find project file: $relativePath")
        return path.toFile().readText()
    }
}
