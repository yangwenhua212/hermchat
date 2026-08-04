package com.eraherm.hermchat.tools

import com.eraherm.hermchat.data.model.ToolCall
import java.util.UUID

/**
 * 「请记住…」→ memory.remember；「还记得… / 回忆…」→ memory.recall。
 * 仅明确话术命中，避免闲聊误触。
 */
object LocalMemoryPlanner {
    fun plan(userText: String): ToolCall? {
        val text = userText.trim()
        if (text.isEmpty()) return null
        planRemember(text)?.let { return it }
        return planRecall(text)
    }

    private fun planRemember(text: String): ToolCall? {
        if (RECALL_HINT.any { text.contains(it) }) return null
        val content = extractRememberContent(text) ?: return null
        if (content.length < 2) return null
        return ToolCall(
            id = UUID.randomUUID().toString(),
            name = MemoryRememberTool.NAME,
            arguments = mapOf("content" to content),
            needConfirm = false,
            title = "写入记忆",
            summary = "将记住：$content",
        )
    }

    private fun planRecall(text: String): ToolCall? {
        if (!RECALL_HINT.any { text.contains(it) }) return null
        val query = extractRecallQuery(text)
        if (query.length < 2) return null
        return ToolCall(
            id = UUID.randomUUID().toString(),
            name = MemoryRecallTool.NAME,
            arguments = mapOf("query" to query),
            needConfirm = false,
            title = "召回记忆",
            summary = "将查找：$query",
        )
    }

    fun extractRememberContent(text: String): String? {
        val t = text.trim()
        for (p in REMEMBER_PREFIX) {
            if (t.startsWith(p)) {
                return t.removePrefix(p).trim().trimStart('：', ':', '，', ',', ' ')
                    .takeIf { it.isNotBlank() }
            }
        }
        val m = REMEMBER_INLINE.find(t) ?: return null
        return m.groupValues[1].trim().takeIf { it.isNotBlank() }
    }

    fun extractRecallQuery(text: String): String {
        var q = text.trim()
        for (h in RECALL_HINT) {
            q = q.replace(h, " ")
        }
        q = q.replace(Regex("\\s+"), " ").trim()
            .trimStart('？', '?', '，', ',', ' ')
        return q.ifBlank { text.trim() }
    }

    private val REMEMBER_PREFIX = listOf(
        "请记住", "帮我记住", "给我记住", "记下：", "记下:", "记住：", "记住:",
    )

    private val REMEMBER_INLINE = Regex(
        """^(?:记住|记一下)(.+)$""",
    )

    private val RECALL_HINT = listOf(
        "还记得", "记不记得", "记得吗", "记得我", "回忆一下", "帮我回忆", "查一下记忆",
    )
}
