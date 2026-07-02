package com.dinnerplan.shared

data class RestaurantSearchSpec(
    val primaryKeywords: List<String>,
    val strictMatchTerms: List<String> = emptyList(),
    val useGenericFallback: Boolean = false,
    val genericFallbackReason: String? = null
) {
    companion object {
        private val japaneseTerms = listOf("日本料理", "日料", "寿司", "居酒屋", "日式拉面", "和食", "刺身", "日餐")
        private val japaneseStrictTerms = japaneseTerms + listOf("日式", "烧鸟", "天妇罗", "鳗鱼饭")

        fun fromQuery(query: String): RestaurantSearchSpec {
            return when {
                japaneseTerms.any { query.contains(it) } ->
                    RestaurantSearchSpec(
                        primaryKeywords = japaneseTerms,
                        strictMatchTerms = japaneseStrictTerms,
                        useGenericFallback = true,
                        genericFallbackReason = "附近未找到日料相关店铺，以下为附近餐厅补位。"
                    )
                query.contains("牛肉面") -> categorySpec("牛肉面")
                query.contains("湘菜") || query.contains("微辣") -> categorySpec("湘菜")
                query.contains("川菜") || query.contains("麻辣") -> categorySpec("川菜")
                query.contains("粤菜") || query.contains("清淡") -> categorySpec("粤菜")
                query.contains("火锅") -> categorySpec("火锅")
                query.contains("烧烤") -> categorySpec("烧烤")
                else -> RestaurantSearchSpec(listOf(query.trim().ifBlank { "餐厅" }))
            }
        }

        fun generic(): RestaurantSearchSpec = RestaurantSearchSpec(listOf("餐厅"))

        private fun categorySpec(keyword: String): RestaurantSearchSpec {
            return RestaurantSearchSpec(
                primaryKeywords = listOf(keyword),
                useGenericFallback = true,
                genericFallbackReason = "附近未找到${keyword}相关店铺，以下为附近餐厅补位。"
            )
        }
    }
}

fun restaurantRelevanceScore(name: String, type: String, spec: RestaurantSearchSpec): Int {
    if (spec.strictMatchTerms.isEmpty()) return 0
    val text = "$name $type"
    return spec.strictMatchTerms.count { text.contains(it) }
}
