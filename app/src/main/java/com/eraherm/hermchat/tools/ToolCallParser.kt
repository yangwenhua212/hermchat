package com.eraherm.hermchat.tools

import com.eraherm.hermchat.data.model.ToolCall
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object ToolCallParser {
    fun extract(assistantText: String): Pair<String, ToolCall?> {
        val block = findJsonObject(assistantText) ?: return assistantText to null
        val call = parse(block) ?: return assistantText to null
        val cleaned = assistantText.replace(block, "")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
            .ifBlank { "需要你确认后，我才能操作手机。" }
        return cleaned to call
    }

    fun parse(jsonText: String): ToolCall? = runCatching {
        val obj = JSONObject(jsonText)
        val type = obj.optString("type")
        val name = obj.optString("name")
        if (type != "tool_call" && name.isBlank()) return null
        if (name.isBlank()) return null

        val argsObj = obj.optJSONObject("arguments") ?: JSONObject()
        val args = buildMap {
            val keys = argsObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                put(key, argsObj.opt(key)?.toString().orEmpty())
            }
        }
        ToolCall(
            id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
            name = name,
            arguments = args,
            // 未授权；执行前由确认卡 / READ_ONLY 策略置为 true
            needConfirm = false,
            title = obj.optString("title").ifBlank { humanTitle(name) },
            summary = obj.optString("summary").ifBlank { summarize(name, args) },
        )
    }.getOrNull()

    private fun findJsonObject(text: String): String? {
        var start = -1
        var depth = 0
        for (i in text.indices) {
            when (text[i]) {
                '{' -> {
                    if (depth == 0) start = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && start >= 0) {
                        val slice = text.substring(start, i + 1)
                        if (slice.contains("tool_call") ||
                            slice.contains("calendar.create") ||
                            slice.contains("alarm.create") ||
                            slice.contains("url.open") ||
                            slice.contains("web.search") ||
                            slice.contains("share.text")
                        ) {
                            return slice
                        }
                        start = -1
                    }
                }
            }
        }
        return null
    }

    private fun humanTitle(name: String): String = when (name) {
        CalendarTool.NAME -> "创建日历事件"
        AlarmTool.NAME -> "设置提醒"
        OpenUrlTool.NAME -> "打开链接"
        WebSearchTool.NAME -> "打开搜索"
        ShareTextTool.NAME -> "分享文本"
        else -> "执行工具：$name"
    }

    fun summarize(name: String, args: Map<String, String>): String {
        return when (name) {
            CalendarTool.NAME -> {
                val title = args["title"] ?: "日程"
                val begin = args["beginMs"]?.toLongOrNull()
                if (begin != null) {
                    "将创建「$title」\n时间：${formatTime(begin)}"
                } else {
                    "将创建「$title」"
                }
            }
            AlarmTool.NAME -> {
                val message = args["message"] ?: "提醒"
                val trigger = args["triggerMs"]?.toLongOrNull()
                if (trigger != null) {
                    "将设置「$message」\n时间：${formatTime(trigger)}"
                } else {
                    "将设置「$message」"
                }
            }
            OpenUrlTool.NAME -> "将打开：${args["url"].orEmpty()}"
            WebSearchTool.NAME -> "将搜索：${args["query"].orEmpty()}"
            ShareTextTool.NAME -> {
                val text = args["text"].orEmpty()
                "将分享：${text.take(80)}${if (text.length > 80) "…" else ""}"
            }
            else -> args.entries.joinToString("\n") { "${it.key}=${it.value}" }
        }
    }

    fun formatTime(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(ms))
}
