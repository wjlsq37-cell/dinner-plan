package com.dinnerplan.chidian

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserFacingMessagesTest {
    @Test
    fun friendlyStatusMessageRemovesInternalDebugWords() {
        val raw = "命中关键词：寿司；接口返回 500；JSON null undefined OpenAI 兼容格式 readableSnippet"

        val message = friendlyStatusMessage(raw, UserMessageContext.Restaurant)

        blockedUserFacingTerms.forEach { term ->
            assertFalse(message.contains(term, ignoreCase = true), "UI message leaked: $term")
        }
        assertEquals("附近餐厅数据暂时加载失败，请稍后再试。", message)
    }

    @Test
    fun commonFailuresMapToFriendlyMessages() {
        assertEquals(
            "暂时没找到合适的结果，可以换个关键词或放宽条件再试。",
            friendlyStatusMessage("mxnzp 暂未找到“晚餐”相关菜谱。", UserMessageContext.SearchEmpty)
        )
        assertEquals(
            "菜谱服务暂时不可用，请稍后再试。",
            friendlyStatusMessage("万维易源接口返回异常（10001）：余额不足", UserMessageContext.Recipe)
        )
        assertEquals(
            "附近餐厅数据暂时加载失败，请稍后再试。",
            friendlyStatusMessage("高德地图请求失败：USERKEY_PLAT_NOMATCH", UserMessageContext.Restaurant)
        )
        assertEquals(
            "暂时无法获取当前位置，你也可以手动输入地点。",
            friendlyStatusMessage("定位失败：provider disabled", UserMessageContext.Location)
        )
        assertEquals(
            "生成过程有点卡住，已为你保留可用结果。",
            friendlyStatusMessage("AI 返回内容不是可解析菜谱 JSON：{bad}", UserMessageContext.Ai)
        )
        assertEquals(
            "相关服务还没配置好，请到设置中检查后再试。",
            friendlyStatusMessage("开发者模式 AI 配置缺失，Key 未填写。", UserMessageContext.Config)
        )
    }

    @Test
    fun friendlyReasonKeepsNaturalCopyButHidesRuleDetails() {
        val restaurantReason = friendlyReason(
            "匹配：命中自助；来自高德 POI；未生成虚假餐厅；null",
            UserReasonContext.Restaurant
        )

        blockedUserFacingTerms.forEach { term ->
            assertFalse(restaurantReason.contains(term, ignoreCase = true), "reason leaked: $term")
        }
        assertEquals("这家店和你的搜索更接近，距离也比较合适。", restaurantReason)
        assertTrue(
            friendlyReason("酸甜开胃，适合晚餐。", UserReasonContext.Recipe).contains("酸甜")
        )
    }
}
