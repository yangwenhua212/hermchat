package com.eraherm.hermchat.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.eraherm.hermchat.data.model.ToolCall
import com.eraherm.hermchat.data.model.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 写入系统剪贴板；[ToolRisk.WRITE]，须用户确认。 */
class ClipboardWriteTool(
    private val context: Context,
) : PhoneTool {
    override val name: String = NAME
    override val requiredPermissions: Array<String> = emptyArray()
    override val risk: ToolRisk = ToolRisk.WRITE

    override suspend fun execute(call: ToolCall): ToolResult = withContext(Dispatchers.Main) {
        val text = call.arguments["text"]?.trim().orEmpty()
        if (text.isBlank()) {
            return@withContext ToolResult(call.id, name, false, "缺少要写入的文本")
        }
        return@withContext try {
            val label = call.arguments["label"]?.trim().orEmpty().ifBlank { "HxSync" }
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText(label, text))
            val preview = text.take(80)
            val more = if (text.length > 80) "…" else ""
            ToolResult(call.id, name, true, "已写入剪贴板：$preview$more")
        } catch (e: Exception) {
            ToolResult(call.id, name, false, e.message ?: "无法写入剪贴板")
        }
    }

    companion object {
        const val NAME = "clipboard.write"
    }
}
