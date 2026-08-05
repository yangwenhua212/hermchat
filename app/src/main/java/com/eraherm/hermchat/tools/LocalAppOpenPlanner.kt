package com.eraherm.hermchat.tools

import com.eraherm.hermchat.data.model.ToolCall
import java.util.UUID

/** 「打开微信 / 打开设置」等 → app.open */
object LocalAppOpenPlanner {
    fun plan(userText: String): ToolCall? {
        val text = userText.trim()
        if (text.isEmpty()) return null
        val m = Regex("""^(?:帮我|请)?(?:打开|启动|运行)\s*(.+)$""").find(text)
            ?: Regex("""^(?:打开|启动)\s*(.+)$""").find(text)
            ?: return null
        var app = m.groupValues[1].trim()
            .removeSuffix("一下")
            .removeSuffix("App")
            .removeSuffix("APP")
            .removeSuffix("应用")
            .trim()
        if (app.isBlank() || app.length > 20) return null
        // 排除链接 / 搜索 / 官网类；官网交给 LocalUrlOpenPlanner
        if (app.startsWith("http") || app.contains("搜索") || app.contains("链接")) return null
        if (app.contains("官网") || app.contains("网站") || app.contains("主页") || app.contains("网页")) {
            return null
        }
        if (LocalMapsPlanner.extractQuery(text) != null) return null
        if (app.startsWith("地图搜") || app.startsWith("地图搜索") || app.contains("导航")) return null
        if (listOf("闹钟", "提醒", "日历", "日程").any { app.contains(it) }) return null
        val args = mapOf("app" to app)
        return ToolCall(
            id = UUID.randomUUID().toString(),
            name = AppOpenTool.NAME,
            arguments = args,
            needConfirm = false,
            title = "打开应用",
            summary = ToolCallParser.summarize(AppOpenTool.NAME, args),
        )
    }
}
