package com.eraherm.hermchat.tools

import com.eraherm.hermchat.data.model.ToolCall
import java.util.UUID

/** 「拨打 10086 / 打电话给 138…」→ phone.dial */
object LocalDialPlanner {
    fun plan(userText: String): ToolCall? {
        val text = userText.trim()
        if (text.isEmpty()) return null
        if (!listOf("拨打", "打电话", "打给", "拨号", "呼叫").any { text.contains(it) }) {
            return null
        }
        val number = Regex("""(?:\+?\d[\d\s\-]{2,18}\d)""")
            .findAll(text)
            .map { it.value }
            .map { PhoneDialTool.normalizeNumber(it) }
            .firstOrNull { it.length in 3..20 }
            ?: return null
        val args = mapOf("number" to number)
        return ToolCall(
            id = UUID.randomUUID().toString(),
            name = PhoneDialTool.NAME,
            arguments = args,
            needConfirm = false,
            title = "打开拨号盘",
            summary = ToolCallParser.summarize(PhoneDialTool.NAME, args),
        )
    }
}
