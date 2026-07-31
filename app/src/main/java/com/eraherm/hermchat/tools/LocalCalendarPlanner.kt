package com.eraherm.hermchat.tools

import com.eraherm.hermchat.data.model.ToolCall
import java.util.Calendar
import java.util.UUID

/**
 * Client-side fallback: turn common Chinese schedule phrases into a confirmable tool_call
 * when the Agent does not emit structured JSON yet.
 */
object LocalCalendarPlanner {
    private val HOUR_PATTERNS = listOf(
        Regex("""下午\s*(\d{1,2})\s*点(?:\s*(\d{1,2})\s*分)?"""),
        Regex("""上午\s*(\d{1,2})\s*点(?:\s*(\d{1,2})\s*分)?"""),
        Regex("""晚上\s*(\d{1,2})\s*点(?:\s*(\d{1,2})\s*分)?"""),
        Regex("""(\d{1,2})\s*点(?:\s*(\d{1,2})\s*分)?"""),
    )

    fun plan(userText: String): ToolCall? {
        val text = userText.trim()
        if (text.isEmpty()) return null
        val looksLikeSchedule = listOf("提醒", "开会", "日程", "日历", "预约", "会议")
            .any { text.contains(it) }
        if (!looksLikeSchedule) return null

        val begin = resolveBegin(text) ?: return null
        val title = extractTitle(text)
        val args = mapOf(
            "title" to title,
            "beginMs" to begin.toString(),
            "endMs" to (begin + 60 * 60 * 1000L).toString(),
            "description" to "由 HxSync 根据「$text」创建",
        )
        return ToolCall(
            id = UUID.randomUUID().toString(),
            name = CalendarTool.NAME,
            arguments = args,
            needConfirm = true,
            title = "创建日历事件",
            summary = ToolCallParser.summarize(CalendarTool.NAME, args),
        )
    }

    private fun resolveBegin(text: String): Long? {
        val cal = Calendar.getInstance()
        when {
            text.contains("后天") -> cal.add(Calendar.DAY_OF_YEAR, 2)
            text.contains("明天") -> cal.add(Calendar.DAY_OF_YEAR, 1)
            text.contains("今天") -> Unit
            else -> cal.add(Calendar.DAY_OF_YEAR, 1) // default tomorrow
        }

        var hour: Int? = null
        var minute = 0
        for (regex in HOUR_PATTERNS) {
            val match = regex.find(text) ?: continue
            hour = match.groupValues[1].toIntOrNull() ?: continue
            minute = match.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
            when {
                text.contains("下午") && hour in 1..11 -> hour += 12
                text.contains("晚上") && hour in 1..11 -> hour += 12
                text.contains("上午") && hour == 12 -> hour = 0
            }
            break
        }
        if (hour == null) {
            hour = 15
            minute = 0
        }
        if (hour !in 0..23 || minute !in 0..59) return null
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun extractTitle(text: String): String {
        val cleaned = text
            .replace("帮我", "")
            .replace("提醒我", "")
            .replace("记得", "")
            .trim()
        val after = Regex("""(?:点|分)\s*(.+)""").find(cleaned)?.groupValues?.getOrNull(1)
        val title = after?.trim().orEmpty()
        return title.ifBlank { "提醒事项" }.take(40)
    }
}
