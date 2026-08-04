package com.eraherm.hermchat.tools

import com.eraherm.hermchat.data.model.ToolCall
import java.util.UUID

/** 「发邮件给 x@y / 写邮件」→ email.compose */
object LocalEmailPlanner {
    fun plan(userText: String): ToolCall? {
        val text = userText.trim()
        if (text.isEmpty()) return null
        if (!HINT.any { text.contains(it) }) return null
        // 「怎么写邮件」类问答不抢工具
        if (isJustAskingHow(text)) return null

        val email = EMAIL.find(text)?.value
        val subject = extractAfter(text, listOf("主题", "标题"))
        val bodyMarked = extractAfter(text, listOf("正文"))
        // 必须有邮箱，或「发/写邮件给」后有明确对象；禁止用剩余碎句当正文误触
        if (email.isNullOrBlank() && !hasComposeTarget(text) && subject.isNullOrBlank() && bodyMarked.isNullOrBlank()) {
            return null
        }
        if (email.isNullOrBlank() && subject.isNullOrBlank() && bodyMarked.isNullOrBlank()) {
            return null
        }

        val args = buildMap {
            if (!email.isNullOrBlank()) put("to", email)
            if (!subject.isNullOrBlank()) put("subject", subject)
            if (!bodyMarked.isNullOrBlank()) put("body", bodyMarked.take(500))
        }
        if (args.isEmpty()) return null

        return ToolCall(
            id = UUID.randomUUID().toString(),
            name = EmailComposeTool.NAME,
            arguments = args,
            needConfirm = false,
            title = "写邮件",
            summary = when {
                !email.isNullOrBlank() -> "将打开邮件给 $email"
                else -> "将打开邮件撰写"
            },
        )
    }

    private fun hasComposeTarget(text: String): Boolean =
        text.contains("发邮件给") || text.contains("写邮件给") || text.contains("邮件发给")

    private fun isJustAskingHow(text: String): Boolean {
        val t = text.trim()
        return (t.contains("怎么") || t.contains("如何") || t.contains("怎样")) &&
            (t.contains("邮件") || t.contains("发"))
    }

    private fun extractAfter(text: String, keys: List<String>): String? {
        for (k in keys) {
            val idx = text.indexOf(k)
            if (idx < 0) continue
            val rest = text.substring(idx + k.length).trim().trimStart('：', ':', ' ')
            val cut = rest.split(Regex("""[，。；\n]""")).firstOrNull()?.trim().orEmpty()
            if (cut.isNotBlank() && cut.length <= 200) return cut
        }
        return null
    }

    private val HINT = listOf("发邮件", "写邮件", "发封邮件", "mailto")
    private val EMAIL = Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")
}
