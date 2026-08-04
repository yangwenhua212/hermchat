package com.eraherm.hermchat.tools

import com.eraherm.hermchat.data.model.ToolCall
import java.util.UUID

/** 「地图搜 / 导航到 / 附近」→ maps.search */
object LocalMapsPlanner {
    fun plan(userText: String): ToolCall? {
        val text = userText.trim()
        if (text.isEmpty()) return null
        val query = extractQuery(text) ?: return null
        if (query.length < 2) return null
        return ToolCall(
            id = UUID.randomUUID().toString(),
            name = MapsSearchTool.NAME,
            arguments = mapOf("query" to query),
            needConfirm = false,
            title = "打开地图",
            summary = "将在地图中搜索「$query」",
        )
    }

    fun extractQuery(text: String): String? {
        val t = text.trim()
        for (p in PREFIX) {
            if (t.startsWith(p)) {
                return t.removePrefix(p).trim().trimStart('：', ':', ' ', '到')
                    .takeIf { it.isNotBlank() }
            }
        }
        val m = INLINE.find(t) ?: return null
        return m.groupValues[1].trim().takeIf { it.isNotBlank() }
    }

    private val PREFIX = listOf(
        "地图搜索", "地图搜", "打开地图搜", "导航到", "导航去", "去地图找",
    )

    private val INLINE = Regex(
        """(?:用地图|在地图上?)(?:搜|搜索|找)(.+)$""",
    )
}
