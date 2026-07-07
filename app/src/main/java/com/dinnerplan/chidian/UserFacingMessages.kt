package com.dinnerplan.chidian

import android.util.Log

internal enum class UserMessageContext {
    General,
    Recipe,
    Restaurant,
    Ai,
    Location,
    SearchEmpty,
    Config
}

internal enum class UserReasonContext {
    MealPlan,
    Recipe,
    Restaurant
}

internal val blockedUserFacingTerms = listOf(
    "命中",
    "匹配规则",
    "调试",
    "接口返回",
    "OpenAI 兼容格式",
    "JSON",
    "readableSnippet",
    "null",
    "undefined",
    "未知错误",
    "未生成虚假",
    "来自高德 POI",
    "POI",
    "Key 类型",
    "USERKEY",
    "AMAP",
    "HTTP"
)

internal fun friendlyStatusMessage(raw: String?, context: UserMessageContext = UserMessageContext.General): String {
    val text = raw.orEmpty().trim()
    val inferred = inferMessageContext(text, context)
    return when (inferred) {
        UserMessageContext.SearchEmpty -> NO_RESULT_MESSAGE
        UserMessageContext.Recipe -> RECIPE_SERVICE_MESSAGE
        UserMessageContext.Restaurant -> RESTAURANT_SERVICE_MESSAGE
        UserMessageContext.Location -> LOCATION_MESSAGE
        UserMessageContext.Ai -> AI_MESSAGE
        UserMessageContext.Config -> CONFIG_MESSAGE
        UserMessageContext.General -> {
            if (text.isBlank()) {
                GENERAL_MESSAGE
            } else if (text.containsDebugToken()) {
                GENERAL_MESSAGE
            } else {
                text.cleanFriendlyText().ifBlank { GENERAL_MESSAGE }
            }
        }
    }
}

internal fun friendlyReason(raw: String?, context: UserReasonContext): String {
    val text = raw.orEmpty().trim()
    if (text.isBlank() || text.containsDebugToken()) {
        return defaultReason(context)
    }
    return text.cleanFriendlyText().ifBlank { defaultReason(context) }
}

internal fun logInternalIssue(tag: String, raw: String?, error: Throwable? = null) {
    val detail = raw.orEmpty().trim()
    if (detail.isBlank() && error == null) return
    val message = if (detail.isBlank()) tag else "$tag: $detail"
    if (error == null) {
        Log.w("ChiDian", message)
    } else {
        Log.w("ChiDian", message, error)
    }
}

private fun inferMessageContext(text: String, preferred: UserMessageContext): UserMessageContext {
    if (text.isBlank()) return preferred
    val lower = text.lowercase()
    return when {
        text.containsAny("暂未找到", "没找到", "无结果", "未找到符合", "empty", "no result") -> UserMessageContext.SearchEmpty
        text.containsAny("未填写", "未配置", "配置缺失") ||
            (preferred == UserMessageContext.Config && text.containsAny("Key", "USERKEY", "app_id", "app_secret", "api_key")) -> UserMessageContext.Config
        text.containsDebugToken() && preferred != UserMessageContext.General -> preferred
        text.containsAny("定位", "当前位置", "位置", "经纬度", "坐标", "provider", "location") -> UserMessageContext.Location
        text.containsAny("高德", "amap", "地图", "餐厅", "POI", "Web Key") -> UserMessageContext.Restaurant
        text.containsAny("AI", "OpenAI", "JSON", "模型", "chat/completions", "响应不是", "解析菜谱") -> UserMessageContext.Ai
        text.containsAny("菜谱", "万维", "mxnzp", "showapi", "recipe", "余额", "次数", "订购") -> UserMessageContext.Recipe
        lower.contains("timeout") || lower.contains("http") -> preferred
        text.containsDebugToken() -> preferred
        else -> preferred
    }
}

private fun String.cleanFriendlyText(): String {
    return replace("null", "", ignoreCase = true)
        .replace("undefined", "", ignoreCase = true)
        .replace(Regex("""\s+"""), " ")
        .trim(' ', '，', '。', '；', ';', ':', '：')
}

private fun String.containsDebugToken(): Boolean {
    return blockedUserFacingTerms.any { contains(it, ignoreCase = true) }
}

private fun String.containsAny(vararg values: String): Boolean {
    return values.any { contains(it, ignoreCase = true) }
}

private fun defaultReason(context: UserReasonContext): String {
    return when (context) {
        UserReasonContext.MealPlan -> "这套餐单比较贴合你的用餐需求，适合按需调整。"
        UserReasonContext.Recipe -> "这道菜和你的需求比较接近，可以点开看看做法。"
        UserReasonContext.Restaurant -> "这家店和你的搜索更接近，距离也比较合适。"
    }
}

private const val NO_RESULT_MESSAGE = "暂时没找到合适的结果，可以换个关键词或放宽条件再试。"
private const val RECIPE_SERVICE_MESSAGE = "菜谱服务暂时不可用，请稍后再试。"
private const val RESTAURANT_SERVICE_MESSAGE = "附近餐厅数据暂时加载失败，请稍后再试。"
private const val LOCATION_MESSAGE = "暂时无法获取当前位置，你也可以手动输入地点。"
private const val AI_MESSAGE = "生成过程有点卡住，已为你保留可用结果。"
private const val CONFIG_MESSAGE = "相关服务还没配置好，请到设置中检查后再试。"
private const val GENERAL_MESSAGE = "服务暂时不可用，请稍后再试。"
