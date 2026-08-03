package com.eraherm.hermchat.tools

import android.content.Context
import android.content.Intent
import com.eraherm.hermchat.data.model.ToolCall
import com.eraherm.hermchat.data.model.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ShareTextTool(
    private val context: Context,
) : PhoneTool {
    override val name: String = NAME
    override val requiredPermissions: Array<String> = emptyArray()

    override suspend fun execute(call: ToolCall): ToolResult = withContext(Dispatchers.Main) {
        val text = call.arguments["text"]?.trim().orEmpty()
        if (text.isBlank()) {
            return@withContext ToolResult(call.id, name, false, "缺少分享内容")
        }
        val title = call.arguments["title"]?.trim().orEmpty().ifBlank { "分享" }
        return@withContext try {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(Intent.EXTRA_SUBJECT, title)
            }
            val chooser = Intent.createChooser(send, title)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            ToolResult(call.id, name, true, "已打开系统分享")
        } catch (e: Exception) {
            ToolResult(call.id, name, false, e.message ?: "无法分享")
        }
    }

    companion object {
        const val NAME = "share.text"
    }
}
