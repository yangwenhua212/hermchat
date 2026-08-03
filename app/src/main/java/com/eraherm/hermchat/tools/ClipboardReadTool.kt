package com.eraherm.hermchat.tools

import android.content.ClipboardManager
import android.content.Context
import com.eraherm.hermchat.data.model.ToolCall
import com.eraherm.hermchat.data.model.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 读取系统剪贴板文本；[ToolRisk.READ_ONLY]，Loop 内可静默执行。 */
class ClipboardReadTool(
    private val context: Context,
) : PhoneTool {
    override val name: String = NAME
    override val requiredPermissions: Array<String> = emptyArray()
    override val risk: ToolRisk = ToolRisk.READ_ONLY

    override suspend fun execute(call: ToolCall): ToolResult = withContext(Dispatchers.Main) {
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip
            if (clip == null || clip.itemCount <= 0) {
                return@withContext ToolResult(call.id, name, true, "剪贴板为空")
            }
            val text = clip.getItemAt(0).coerceToText(context)?.toString().orEmpty()
            if (text.isBlank()) {
                return@withContext ToolResult(call.id, name, true, "剪贴板无文本")
            }
            val capped = text.take(MAX_CHARS)
            val suffix = if (text.length > MAX_CHARS) "…(已截断，共 ${text.length} 字)" else ""
            ToolResult(call.id, name, true, "剪贴板内容：$capped$suffix")
        } catch (e: Exception) {
            ToolResult(call.id, name, false, e.message ?: "无法读取剪贴板")
        }
    }

    companion object {
        const val NAME = "clipboard.read"
        private const val MAX_CHARS = 1500
    }
}
