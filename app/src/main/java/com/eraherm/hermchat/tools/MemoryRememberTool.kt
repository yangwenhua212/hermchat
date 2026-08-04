package com.eraherm.hermchat.tools

import android.content.Context
import com.eraherm.hermchat.HermChatApp
import com.eraherm.hermchat.data.model.ToolCall
import com.eraherm.hermchat.data.model.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 写入本机本地记忆；须确认。 */
class MemoryRememberTool(
    private val context: Context,
) : PhoneTool {
    override val name: String = NAME
    override val requiredPermissions: Array<String> = emptyArray()
    override val risk: ToolRisk = ToolRisk.WRITE

    override suspend fun execute(call: ToolCall): ToolResult = withContext(Dispatchers.IO) {
        val store = (context.applicationContext as? HermChatApp)?.memoryStore
            ?: return@withContext ToolResult(call.id, name, false, "本地记忆不可用")
        if (!store.isReady()) {
            return@withContext ToolResult(call.id, name, false, "未开启本地记忆")
        }
        val content = call.arguments["content"]?.trim().orEmpty()
            .ifBlank { call.arguments["text"]?.trim().orEmpty() }
            .ifBlank { call.arguments["memory"]?.trim().orEmpty() }
        if (content.isBlank()) {
            return@withContext ToolResult(call.id, name, false, "缺少 content")
        }
        val pinned = call.arguments["pinned"]
            ?.equals("true", ignoreCase = true) == true
        store.remember(content, pinned = pinned).fold(
            onSuccess = { r ->
                val pin = if (r.pinned) "（已钉死）" else ""
                ToolResult(call.id, name, true, "已写入本机记忆$pin")
            },
            onFailure = { e ->
                ToolResult(call.id, name, false, e.message ?: "写入失败")
            },
        )
    }

    companion object {
        const val NAME = "memory.remember"
    }
}
