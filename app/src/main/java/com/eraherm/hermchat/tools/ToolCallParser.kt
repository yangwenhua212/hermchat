package com.eraherm.hermchat.tools

import com.eraherm.hermchat.data.model.ToolCall
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object ToolCallParser {
    fun extract(assistantText: String): Pair<String, ToolCall?> {
        val normalized = stripCodeFences(assistantText)
        val block = findJsonObject(normalized) ?: return assistantText to null
        val call = parse(block) ?: return assistantText to null
        val cleaned = stripCodeFences(assistantText.replace(block, ""))
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
            .ifBlank { "好的。" }
        return cleaned to call
    }

    /** 去掉 ```json ... ```，便于模型包在代码块里仍能解析。 */
    fun stripCodeFences(text: String): String =
        text.replace(Regex("```(?:json|JSON)?\\s*", RegexOption.IGNORE_CASE), "")
            .replace("```", "")

    fun parse(jsonText: String): ToolCall? = runCatching {
        val obj = JSONObject(jsonText)
        val type = obj.optString("type")
        val rawName = obj.optString("name")
        if (type != "tool_call" && rawName.isBlank()) return null
        if (rawName.isBlank()) return null
        val name = normalizeToolName(rawName)

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

    fun normalizeToolName(raw: String): String {
        val n = raw.trim().lowercase().replace('-', '_').replace(' ', '_')
        return when (n) {
            "alarm.create", "alarm_create", "set_alarm", "create_alarm", "alarm" -> AlarmTool.NAME
            "calendar.create", "calendar_create", "create_calendar", "calendar" -> CalendarTool.NAME
            "url.open", "url_open", "open_url", "open.url" -> OpenUrlTool.NAME
            "web.search", "web_search", "search" -> WebSearchTool.NAME
            "share.text", "share_text", "share" -> ShareTextTool.NAME
            "clipboard.read", "clipboard_read", "read_clipboard" -> ClipboardReadTool.NAME
            "clipboard.write", "clipboard_write", "write_clipboard" -> ClipboardWriteTool.NAME
            "app.open", "app_open", "open_app", "launch_app" -> AppOpenTool.NAME
            "phone.dial", "phone_dial", "dial", "call" -> PhoneDialTool.NAME
            "memory.recall", "memory_recall", "recall", "memory.search" -> MemoryRecallTool.NAME
            "memory.remember", "memory_remember", "remember", "memory.write" -> MemoryRememberTool.NAME
            else -> raw.trim()
        }
    }

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
                            slice.contains("set_alarm") ||
                            slice.contains("url.open") ||
                            slice.contains("web.search") ||
                            slice.contains("share.text") ||
                            slice.contains("clipboard.read") ||
                            slice.contains("clipboard.write") ||
                            slice.contains("app.open") ||
                            slice.contains("phone.dial") ||
                            slice.contains("memory.recall") ||
                            slice.contains("memory.remember")
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
        ClipboardReadTool.NAME -> "读取剪贴板"
        ClipboardWriteTool.NAME -> "写入剪贴板"
        AppOpenTool.NAME -> "打开应用"
        PhoneDialTool.NAME -> "打开拨号盘"
        MemoryRecallTool.NAME -> "召回记忆"
        MemoryRememberTool.NAME -> "写入记忆"
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
                val trigger = AlarmTool.parseTriggerMs(args)
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
            ClipboardReadTool.NAME -> "读取当前剪贴板文本"
            ClipboardWriteTool.NAME -> {
                val text = args["text"].orEmpty()
                "将写入剪贴板：${text.take(80)}${if (text.length > 80) "…" else ""}"
            }
            AppOpenTool.NAME -> {
                val app = args["app"].orEmpty().ifBlank {
                    args["package"].orEmpty().ifBlank { args["packageName"].orEmpty() }
                }
                "将打开应用「$app」"
            }
            PhoneDialTool.NAME -> {
                val num = args["number"].orEmpty()
                    .ifBlank { args["phone"].orEmpty() }
                "将打开拨号盘：$num"
            }
            MemoryRecallTool.NAME -> {
                val q = args["query"].orEmpty().ifBlank { args["q"].orEmpty() }
                "将查找记忆：$q"
            }
            MemoryRememberTool.NAME -> {
                val c = args["content"].orEmpty().ifBlank { args["text"].orEmpty() }
                "将记住：${c.take(80)}${if (c.length > 80) "…" else ""}"
            }
            else -> args.entries.joinToString("\n") { "${it.key}=${it.value}" }
        }
    }

    fun formatTime(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(ms))
}
