package com.eraherm.hermchat.tools

import com.eraherm.hermchat.data.model.ToolCall

/** Prefer alarm for timers / wake-ups; fall back to calendar for meetings. */
object LocalToolPlanner {
    fun plan(userText: String): ToolCall? {
        val text = userText.trim()
        if (text.isEmpty()) return null
        // 多步（查天气再提醒等）交给云脑 Loop，避免端侧抢先只出闹钟
        if (looksMultiStep(text)) return null
        return LocalAlarmPlanner.plan(text)
            ?: LocalDialPlanner.plan(text)
            ?: LocalMapsPlanner.plan(text)
            ?: LocalEmailPlanner.plan(text)
            ?: LocalAppOpenPlanner.plan(text)
            ?: LocalMemoryPlanner.plan(text)
            ?: LocalCalendarPlanner.plan(text)
    }

    /** 「查…然后提醒」类：必须让 API 先出第一步 tool。 */
    fun looksMultiStep(text: String): Boolean {
        val hasRemind = listOf("提醒", "闹钟", "叫我", "倒计时", "日程", "日历").any {
            text.contains(it)
        }
        if (!hasRemind) return false
        val chain = listOf("然后", "接着", "并且", "之后再", "完了再")
        val otherAct = listOf("查", "搜索", "天气", "打开", "分享", "搜一下", "看一下")
        return chain.any { text.contains(it) } ||
            (otherAct.any { text.contains(it) } && text.length >= 10)
    }
}
