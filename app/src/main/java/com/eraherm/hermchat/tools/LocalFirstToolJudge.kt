package com.eraherm.hermchat.tools

import com.eraherm.hermchat.data.model.ToolCall
import com.eraherm.hermchat.data.network.GatewayRouter

/**
 * 实验「本地优先解析」：判断本地首轮输出是否可用；不可用则本轮改云端。
 */
object LocalFirstToolJudge {
    fun accept(raw: String, tool: ToolCall?): Boolean {
        if (tool != null) return true
        val t = raw.trim()
        if (t.length < 2) return false
        if (GatewayRouter.isWeakLocalReply(t)) return false
        if (looksLikeBrokenToolAttempt(t)) return false
        return true
    }

    /** 提到了工具格式却解析不出合法 tool_call → 当作失败。 */
    fun looksLikeBrokenToolAttempt(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false
        return TOOL_MARKERS.any { t.contains(it, ignoreCase = true) }
    }

    private val TOOL_MARKERS = listOf(
        "tool_call",
        "alarm.create",
        "calendar.create",
        "url.open",
        "web.search",
        "share.text",
        "clipboard.read",
        "clipboard.write",
    )
}
