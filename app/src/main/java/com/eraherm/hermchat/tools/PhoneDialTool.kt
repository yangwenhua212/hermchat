package com.eraherm.hermchat.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.eraherm.hermchat.data.model.ToolCall
import com.eraherm.hermchat.data.model.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 打开系统拨号盘并填入号码（ACTION_DIAL，不直接外呼，须确认）。
 */
class PhoneDialTool(
    private val context: Context,
) : PhoneTool {
    override val name: String = NAME
    override val requiredPermissions: Array<String> = emptyArray()

    override suspend fun execute(call: ToolCall): ToolResult = withContext(Dispatchers.Main) {
        val raw = call.arguments["number"]?.trim().orEmpty()
            .ifBlank { call.arguments["phone"]?.trim().orEmpty() }
            .ifBlank { call.arguments["tel"]?.trim().orEmpty() }
        val digits = normalizeNumber(raw)
        if (digits.isBlank()) {
            return@withContext ToolResult(call.id, name, false, "缺少电话号码")
        }
        if (digits.length < 3 || digits.length > 20) {
            return@withContext ToolResult(call.id, name, false, "号码长度不合理")
        }
        return@withContext try {
            val uri = Uri.parse("tel:$digits")
            val intent = Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            ToolResult(call.id, name, true, "已打开拨号盘：$digits")
        } catch (e: Exception) {
            ToolResult(call.id, name, false, e.message ?: "无法打开拨号盘")
        }
    }

    companion object {
        const val NAME = "phone.dial"

        fun normalizeNumber(raw: String): String {
            val t = raw.trim()
            if (t.startsWith("+")) {
                return "+" + t.drop(1).filter { it.isDigit() }
            }
            return t.filter { it.isDigit() }
        }
    }
}
