package com.dinnerplan.chidian

internal const val WANWEI_RECIPE_BASE_URL = "https://route.showapi.com"

internal data class DeveloperSettings(
    val enabled: Boolean = false,
    val aiBaseUrl: String = "",
    val aiApiKey: String = "",
    val aiModel: String = "",
    val amapWebKey: String = "",
    val wanweiRecipeAppKey: String = "",
    val wanweiRecipePageSize: Int = 20,
    val maxWaitSeconds: Int = 180
) {
    val safePageSize: Int
        get() = wanweiRecipePageSize.coerceIn(1, 50)

    val safeMaxWaitSeconds: Int
        get() = maxWaitSeconds.coerceIn(10, 300)

    val maxWaitMillis: Long
        get() = safeMaxWaitSeconds * 1_000L
}
