package com.eraherm.hermchat.tools

import com.eraherm.hermchat.data.model.ToolCall

/**
 * ④ Loop：模型该出 tool_call 却吐错 / 假装已执行时，触发一轮「只吐 JSON」挽救。
 */
object ToolCallRepair {
    fun shouldRetry(
        userPrompt: String,
        assistantRaw: String,
        parsedTool: ToolCall?,
        localFallback: ToolCall?,
    ): Boolean {
        if (parsedTool != null) return false
        // 端侧话术已能兜底时不必再打云端
        if (localFallback != null) return false
        val raw = assistantRaw.trim()
        if (raw.isEmpty()) return expectsPhoneTool(userPrompt)
        if (LocalFirstToolJudge.looksLikeBrokenToolAttempt(raw)) return true
        if (looksLikeFakeToolSuccess(raw) && expectsPhoneTool(userPrompt)) return true
        if (expectsPhoneTool(userPrompt) && looksLikeEmptyAck(raw)) return true
        return false
    }

    fun nudgeMessage(userPrompt: String): String = buildString {
        appendLine(LocalToolsPrompt.userPrefix().trimEnd())
        appendLine("上轮没有给出可执行的 tool_call（或格式错误 / 假装已执行）。")
        appendLine("用户原话：$userPrompt")
        appendLine("请只输出一个 tool_call JSON，不要代码块，不要假装已经操作成功。")
        appendLine(
            """格式：{"type":"tool_call","name":"工具名","arguments":{...},"title":"短标题","summary":"确认摘要"}""",
        )
        append("若需多步，先输出第一步。triggerMs 必须是 Unix 毫秒。")
    }

    fun continueNudgeMessage(): String = buildString {
        appendLine("上轮回复无法解析为 tool_call。")
        appendLine("若任务未完成：只输出下一个 tool_call JSON（不要代码块）。")
        append("若已完成：用一两句中文收尾，不要再提已成功的工具。")
    }

    fun expectsPhoneTool(userPrompt: String): Boolean {
        val t = userPrompt.trim()
        if (t.isEmpty()) return false
        if (isJustAskingHow(t)) return false
        if (LocalToolPlanner.looksMultiStep(t)) return true
        return TOOL_INTENT.any { t.contains(it) }
    }

    private fun isJustAskingHow(text: String): Boolean {
        val t = text.trim()
        return (t.contains("怎么") || t.contains("如何") || t.contains("怎样")) &&
            (t.contains("设") || t.contains("用") || t.length < 40)
    }

    private fun looksLikeFakeToolSuccess(text: String): Boolean =
        FAKE_DONE.any { text.contains(it) }

    private fun looksLikeEmptyAck(text: String): Boolean {
        val t = text.trim()
        if (t.length > 60) return false
        if (t.contains("？") || t.contains("?")) return false
        return EMPTY_ACK.any { ack ->
            t == ack || t == "$ack。" || t.startsWith("$ack，") || t.startsWith("$ack,")
        }
    }

    private val TOOL_INTENT = listOf(
        "提醒", "闹钟", "叫我", "倒计时", "日程", "日历", "开会",
        "打开", "启动", "搜索", "搜一下", "查一下", "分享", "剪贴板",
        "拨打", "打电话", "打给", "拨号", "呼叫",
        "记住", "记得", "回忆", "长记忆",
        "地图", "导航", "发邮件", "写邮件",
    )

    private val FAKE_DONE = listOf(
        "已设置", "已经设", "已帮你设", "设好了", "已创建", "已经创建",
        "已打开", "已经打开", "已分享", "已写入剪贴板", "提醒已", "已拨",
        "已记住", "已经记住", "已写入记忆", "已发邮件", "邮件已",
    )

    private val EMPTY_ACK = listOf("好的", "收到", "嗯", "行", "可以", "没问题", "明白")
}
