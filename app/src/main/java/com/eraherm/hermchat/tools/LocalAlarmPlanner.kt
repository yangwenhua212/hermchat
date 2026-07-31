package com.eraherm.hermchat.tools

import com.eraherm.hermchat.data.model.ToolCall
import java.util.Calendar
import java.util.UUID

/**
 * Client-side planner for alarm / relative reminders.
 * Prefer this over calendar when the phrase is clearly a wake-up / timer-style remind.
 */
object LocalAlarmPlanner {
    private val RELATIVE = listOf(
        Regex("""(\d+)\s*分钟后"""),
        Regex("""(\d+)\s*分后"""),
        Regex("""半小时后"""),
        Regex("""(\d+)\s*小时后"""),
        Regex("""一个小时后"""),
        Regex("""1\s*小时后"""),
    )

    fun plan(userText: String): ToolCall? {
        val text = userText.trim()
        if (text.isEmpty()) return null

        val looksAlarm = listOf("闹钟", "叫我", "分钟后", "小时后", "半小时").any { text.contains(it) }
        val looksCalendarMeeting = listOf("开会", "会议", "日程", "日历", "预约").any { text.contains(it) }
        if (!looksAlarm && !(text.contains("提醒") && !looksCalendarMeeting && hasRelative(text))) {
            return null
        }
        // "明天下午3点提醒我开会" → calendar, not alarm
        if (looksCalendarMeeting && !text.contains("闹钟") && !text.contains("叫我")) {
            return null
        }

        val trigger = resolveTrigger(text) ?: return null
        val message = extractMessage(text)
        val args = mapOf(
            "message" to message,
            "triggerMs" to trigger.toString(),
        )
        return ToolCall(
            id = UUID.randomUUID().toString(),
            name = AlarmTool.NAME,
            arguments = args,
            needConfirm = true,
            title = "设置提醒",
            summary = ToolCallParser.summarize(AlarmTool.NAME, args),
        )
    }

    private fun hasRelative(text: String): Boolean =
        RELATIVE.any { it.containsMatchIn(text) } || text.contains("半小时后")

    private fun resolveTrigger(text: String): Long? {
        val now = Calendar.getInstance()

        Regex("""(\d+)\s*分钟后""").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
            now.add(Calendar.MINUTE, it)
            return now.timeInMillis
        }
        Regex("""(\d+)\s*分后""").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
            now.add(Calendar.MINUTE, it)
            return now.timeInMillis
        }
        if (text.contains("半小时后")) {
            now.add(Calendar.MINUTE, 30)
            return now.timeInMillis
        }
        if (text.contains("一个小时后") || text.contains("1小时后") || text.contains("1 小时后")) {
            now.add(Calendar.HOUR_OF_DAY, 1)
            return now.timeInMillis
        }
        Regex("""(\d+)\s*小时后""").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
            now.add(Calendar.HOUR_OF_DAY, it)
            return now.timeInMillis
        }

        // Absolute morning alarm e.g. 明天早上8点叫我 / 闹钟
        if (text.contains("叫我") || text.contains("闹钟")) {
            when {
                text.contains("后天") -> now.add(Calendar.DAY_OF_YEAR, 2)
                text.contains("明天") -> now.add(Calendar.DAY_OF_YEAR, 1)
                text.contains("今天") -> Unit
                else -> {
                    // if only hour given without day, use today if still ahead else tomorrow
                }
            }
            val hourMatch = Regex("""(?:早上|上午|早晨)?\s*(\d{1,2})\s*点(?:\s*(\d{1,2})\s*分)?""")
                .find(text)
            var hour = hourMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 8
            val minute = hourMatch?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0
            if (text.contains("下午") && hour in 1..11) hour += 12
            if (text.contains("晚上") && hour in 1..11) hour += 12
            now.set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            now.set(Calendar.MINUTE, minute.coerceIn(0, 59))
            now.set(Calendar.SECOND, 0)
            now.set(Calendar.MILLISECOND, 0)
            if (now.timeInMillis <= System.currentTimeMillis()) {
                now.add(Calendar.DAY_OF_YEAR, 1)
            }
            return now.timeInMillis
        }
        return null
    }

    private fun extractMessage(text: String): String {
        val cleaned = text
            .replace(Regex("""\d+\s*分钟后"""), "")
            .replace(Regex("""\d+\s*分后"""), "")
            .replace(Regex("""\d+\s*小时后"""), "")
            .replace("半小时后", "")
            .replace("一个小时后", "")
            .replace("提醒我", "")
            .replace("叫我", "")
            .replace("闹钟", "")
            .replace("帮我", "")
            .trim()
        return cleaned.ifBlank { "提醒" }.take(40)
    }
}
