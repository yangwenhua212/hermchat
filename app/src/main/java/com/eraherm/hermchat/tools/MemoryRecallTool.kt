package com.eraherm.hermchat.tools

import android.content.Context
import com.eraherm.hermchat.HermChatApp
import com.eraherm.hermchat.data.model.ToolCall
import com.eraherm.hermchat.data.model.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 本机本地记忆召回；[ToolRisk.READ_ONLY]，可静默执行。 */
class MemoryRecallTool(
    private val context: Context,
) : PhoneTool {
    override val name: String = NAME
    override val requiredPermissions: Array<String> = emptyArray()
    override val risk: ToolRisk = ToolRisk.READ_ONLY

    override suspend fun execute(call: ToolCall): ToolResult = withContext(Dispatchers.IO) {
        val store = (context.applicationContext as? HermChatApp)?.memoryStore
            ?: return@withContext ToolResult(call.id, name, false, "本地记忆不可用")
        if (!store.isReady()) {
            return@withContext ToolResult(call.id, name, false, "未开启本地记忆")
        }
        val query = call.arguments["query"]?.trim().orEmpty()
            .ifBlank { call.arguments["q"]?.trim().orEmpty() }
            .ifBlank { call.arguments["text"]?.trim().orEmpty() }
        if (query.isBlank()) {
            return@withContext ToolResult(call.id, name, false, "缺少 query")
        }
        val topK = call.arguments["top_k"]?.toIntOrNull()
            ?: call.arguments["topK"]?.toIntOrNull()
            ?: 5
        store.recall(query, topK).fold(
            onSuccess = { items ->
                if (items.isEmpty()) {
                    ToolResult(call.id, name, true, "未找到相关记忆")
                } else {
                    val detail = items.joinToString("\n") { item ->
                        val pin = if (item.pinned) "[钉] " else ""
                        "$pin${item.content}"
                    }
                    ToolResult(call.id, name, true, "召回 ${items.size} 条：\n$detail")
                }
            },
            onFailure = { e ->
                ToolResult(call.id, name, false, e.message ?: "召回失败")
            },
        )
    }

    companion object {
        const val NAME = "memory.recall"
    }
}
