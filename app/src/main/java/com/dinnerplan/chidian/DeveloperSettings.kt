package com.dinnerplan.chidian

internal const val WANWEI_RECIPE_BASE_URL = "https://route.showapi.com"
internal const val MXNZP_RECIPE_API_URL = "https://www.mxnzp.com/api/cookbook/search"
internal const val MXNZP_RECIPE_DETAIL_URL = "https://www.mxnzp.com/api/cookbook/details"
internal const val DEEPSEEK_AI_BASE_URL = "https://api.deepseek.com"
internal const val DEEPSEEK_MODEL_FLASH = "deepseek-v4-flash"
internal const val DEEPSEEK_MODEL_PRO = "deepseek-v4-pro"

internal enum class AiProvider(
    val id: String,
    val label: String
) {
    DeepSeek("deepseek", "DeepSeek"),
    Custom("custom", "自定义 AI");

    companion object {
        fun fromId(id: String): AiProvider {
            return entries.firstOrNull { it.id == id } ?: DeepSeek
        }
    }
}

internal enum class RecipeApiSource(
    val id: String,
    val label: String,
    val defaultApiUrl: String
) {
    Wanwei("wanwei", "万维易源", WANWEI_RECIPE_BASE_URL),
    Mxnzp("mxnzp", "mxnzp", MXNZP_RECIPE_API_URL),
    Custom("custom", "自定义", "");

    companion object {
        fun fromId(id: String): RecipeApiSource {
            return entries.firstOrNull { it.id == id } ?: Wanwei
        }
    }
}

internal data class DeveloperSettings(
    val enabled: Boolean = false,
    val aiProvider: String = AiProvider.DeepSeek.id,
    val aiBaseUrl: String = DEEPSEEK_AI_BASE_URL,
    val aiApiKey: String = "",
    val aiModel: String = DEEPSEEK_MODEL_FLASH,
    val amapWebKey: String = "",
    val recipeApiSource: String = RecipeApiSource.Wanwei.id,
    val recipeApiBaseUrl: String = "",
    val recipeApiAppId: String = "",
    val recipeApiSecret: String = "",
    val wanweiRecipeAppKey: String = "",
    val wanweiRecipePageSize: Int = 20,
    val maxWaitSeconds: Int = 180
) {
    val selectedAiProvider: AiProvider
        get() = AiProvider.fromId(aiProvider)

    val selectedRecipeApiSource: RecipeApiSource
        get() = RecipeApiSource.fromId(recipeApiSource)

    val effectiveRecipeApiUrl: String
        get() = recipeApiBaseUrl.trim().trimEnd('/').ifBlank { selectedRecipeApiSource.defaultApiUrl }

    val recipeSourceLabel: String
        get() = selectedRecipeApiSource.label

    val safePageSize: Int
        get() = wanweiRecipePageSize.coerceIn(1, 50)

    val safeMaxWaitSeconds: Int
        get() = maxWaitSeconds.coerceIn(10, 300)

    val maxWaitMillis: Long
        get() = safeMaxWaitSeconds * 1_000L
}
