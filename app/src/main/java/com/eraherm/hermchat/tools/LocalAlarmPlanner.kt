package com.eraherm.hermchat.tools

import com.eraherm.hermchat.data.model.ToolCall
import java.util.Calendar
import java.util.UUID

/**
 * Client-side planner for alarm / relative reminders.
 * Prefer this over calendar when the phrase is clearly a wake-up / timer-style remind.
 */
object LocalAlarmPlanner {
    fun plan(userText: String): ToolCall? {
        val text = userText.trim()
        if (text.isEmpty()) return null

        val looksAlarm = listOf("闹钟", "叫我", "分钟后", "分后", "小时后", "半小时", "倒计时").any {
            text.contains(it)
        }
        val looksCalendarMeeting = listOf("开会", "会议", "日程", "日历", "预约").any {
            text.contains(it)
        }
        val looksRemind = text.contains("提醒")
        if (!looksAlarm && !(looksRemind && !looksCalendarMeeting && (hasRelative(text) || hasClock(text)))) {
            return null
        }
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
            needConfirm = false,
            title = "设置提醒",
            summary = ToolCallParser.summarize(AlarmTool.NAME, args),
        )
    }

    private fun hasRelative(text: String): Boolean =
        Regex("""\d+\s*分钟后""").containsMatchIn(text) ||
            Regex("""\d+\s*分后""").containsMatchIn(text) ||
            Regex("""过\s*\d+\s*分钟""").containsMatchIn(text) ||
            Regex("""\d+\s*分钟(?:后)?(?:再)?提醒""").containsMatchIn(text) ||
            Regex("""提醒(?:我)?(?:一下)?\s*\d+\s*分钟""").containsMatchIn(text) ||
            text.contains("半小时") ||
            Regex("""\d+\s*小时后""").containsMatchIn(text) ||
            text.contains("一个小时后")

    private fun hasClock(text: String): Boolean =
        Regex("""\d{1,2}\s*[:：]\s*\d{2}""").containsMatchIn(text) ||
            Regex("""\d{1,2}\s*点""").containsMatchIn(text) ||
            text.contains("早上") ||
            text.contains("上午") ||
            text.contains("下午") ||
            text.contains("晚上") ||
            text.contains("早晨")

    private fun resolveTrigger(text: String): Long? {
        val now = Calendar.getInstance()

        Regex("""(\d+)\s*分钟后""").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
            now.add(Calendar.MINUTE, it.coerceIn(1, 24 * 60))
            return now.timeInMillis
        }
        Regex("""(\d+)\s*分后""").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
            now.add(Calendar.MINUTE, it.coerceIn(1, 24 * 60))
            return now.timeInMillis
        }
        Regex("""过\s*(\d+)\s*分钟""").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
            now.add(Calendar.MINUTE, it.coerceIn(1, 24 * 60))
            return now.timeInMillis
        }
        Regex("""(\d+)\s*分钟(?:后)?(?:再)?提醒""").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
            now.add(Calendar.MINUTE, it.coerceIn(1, 24 * 60))
            return now.timeInMillis
        }
        Regex("""提醒(?:我)?(?:一下)?\s*(\d+)\s*分钟""").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
            now.add(Calendar.MINUTE, it.coerceIn(1, 24 * 60))
            return now.timeInMillis
        }
        if (text.contains("半小时")) {
            now.add(Calendar.MINUTE, 30)
            return now.timeInMillis
        }
        if (text.contains("一个小时后") || text.contains("1小时后") || text.contains("1 小时后")) {
            now.add(Calendar.HOUR_OF_DAY, 1)
            return now.timeInMillis
        }
        Regex("""(\d+)\s*小时后""").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
            now.add(Calendar.HOUR_OF_DAY, it.coerceIn(1, 48))
            return now.timeInMillis
        }

        val allowAbsolute = text.contains("叫我") ||
            text.contains("闹钟") ||
            (text.contains("提醒") && hasClock(text))
        if (!allowAbsolute) return null

        when {
            text.contains("后天") -> now.add(Calendar.DAY_OF_YEAR, 2)
            text.contains("明天") -> now.add(Calendar.DAY_OF_YEAR, 1)
            text.contains("今天") -> Unit
        }

        Regex("""(\d{1,2})\s*[:：]\s*(\d{2})""").find(text)?.let { m ->
            var hour = m.groupValues[1].toIntOrNull() ?: return@let
            val minute = m.groupValues[2].toIntOrNull() ?: 0
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

        val hourMatch = Regex("""(?:早上|上午|早晨|下午|晚上)?\s*(\d{1,2})\s*点(?:\s*(\d{1,2})\s*分)?""")
            .find(text)
        if (hourMatch != null) {
            var hour = hourMatch.groupValues.getOrNull(1)?.toIntOrNull() ?: return null
            val minute = hourMatch.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
            if (text.contains("下午") && hour in 1..11) hour += 12
            if (text.contains("晚上") && hour in 1..11) hour += 12
            if ((text.contains("早上") || text.contains("上午") || text.contains("早晨")) && hour == 12) {
                hour = 0
            }
            now.set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            now.set(Calendar.MINUTE, minute.coerceIn(0, 59))
            now.set(Calendar.SECOND, 0)
            now.set(Calendar.MILLISECOND, 0)
            if (now.timeInMillis <= System.currentTimeMillis()) {
                now.add(Calendar.DAY_OF_YEAR, 1)
            }
            return now.timeInMillis
        }

        // 「明天早上叫我」无具体点 → 默认 8:00
        if ((text.contains("早上") || text.contains("早晨") || text.contains("上午")) &&
            (text.contains("叫我") || text.contains("闹钟") || text.contains("提醒"))
        ) {
            now.set(Calendar.HOUR_OF_DAY, 8)
            now.set(Calendar.MINUTE, 0)
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
            .replace(Regex("""过\s*\d+\s*分钟"""), "")
            .replace(Regex("""\d+\s*分钟后"""), "")
            .replace(Regex("""\d+\s*分后"""), "")
            .replace(Regex("""\d+\s*小时后"""), "")
            .replace(Regex("""\d+\s*分钟(?:后)?(?:再)?提醒"""), "提醒")
            .replace(Regex("""提醒(?:我)?(?:一下)?\s*\d+\s*分钟"""), "提醒")
            .replace(Regex("""\d{1,2}\s*[:：]\s*\d{2}"""), "")
            .replace("半小时后", "")
            .replace("半小时", "")
            .replace("一个小时后", "")
            .replace("提醒我", "")
            .replace("叫我", "")
            .replace("闹钟", "")
            .replace("帮我", "")
            .replace("设置", "")
            .replace("一下", "")
            .trim()
        return cleaned.ifBlank { "提醒" }.take(40)
    }
}
