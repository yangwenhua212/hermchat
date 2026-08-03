package com.eraherm.hermchat.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.eraherm.hermchat.data.model.ToolCall
import com.eraherm.hermchat.data.model.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OpenUrlTool(
    private val context: Context,
) : PhoneTool {
    override val name: String = NAME
    override val requiredPermissions: Array<String> = emptyArray()

    override suspend fun execute(call: ToolCall): ToolResult = withContext(Dispatchers.Main) {
        val raw = call.arguments["url"]?.trim().orEmpty()
        if (raw.isBlank()) {
            return@withContext ToolResult(call.id, name, false, "缺少 url")
        }
        val uri = runCatching { Uri.parse(raw) }.getOrNull()
        val scheme = uri?.scheme?.lowercase().orEmpty()
        if (scheme != "http" && scheme != "https") {
            return@withContext ToolResult(call.id, name, false, "只允许 http/https 链接")
        }
        return@withContext try {
            val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            ToolResult(call.id, name, true, "已打开链接")
        } catch (e: Exception) {
            ToolResult(call.id, name, false, e.message ?: "无法打开链接")
        }
    }

    companion object {
        const val NAME = "url.open"
    }
}
