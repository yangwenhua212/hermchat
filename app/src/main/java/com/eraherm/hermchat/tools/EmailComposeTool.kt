package com.eraherm.hermchat.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.eraherm.hermchat.data.model.ToolCall
import com.eraherm.hermchat.data.model.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** 打开系统邮件撰写（须确认；不直接发送）。 */
class EmailComposeTool(
    private val context: Context,
) : PhoneTool {
    override val name: String = NAME
    override val requiredPermissions: Array<String> = emptyArray()

    override suspend fun execute(call: ToolCall): ToolResult = withContext(Dispatchers.Main) {
        val to = call.arguments["to"]?.trim().orEmpty()
            .ifBlank { call.arguments["email"]?.trim().orEmpty() }
        val subject = call.arguments["subject"]?.trim().orEmpty()
        val body = call.arguments["body"]?.trim().orEmpty()
            .ifBlank { call.arguments["text"]?.trim().orEmpty() }
        if (to.isBlank() && subject.isBlank() && body.isBlank()) {
            return@withContext ToolResult(call.id, name, false, "缺少收件人或正文")
        }
        return@withContext try {
            val uri = Uri.parse(buildMailto(to, subject, body))
            val intent = Intent(Intent.ACTION_SENDTO, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(context.packageManager) == null) {
                // 部分机型 SENDTO 无响应，回退 ACTION_SEND
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "message/rfc822"
                    if (to.isNotBlank()) putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
                    if (subject.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, subject)
                    if (body.isNotBlank()) putExtra(Intent.EXTRA_TEXT, body)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(send, "写邮件").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } else {
                context.startActivity(intent)
            }
            val tip = when {
                to.isNotBlank() -> "已打开邮件：$to"
                else -> "已打开邮件撰写"
            }
            ToolResult(call.id, name, true, tip)
        } catch (e: Exception) {
            ToolResult(call.id, name, false, e.message ?: "无法打开邮件")
        }
    }

    companion object {
        const val NAME = "email.compose"

        fun buildMailto(to: String, subject: String, body: String): String {
            val enc = StandardCharsets.UTF_8.name()
            val q = buildList {
                if (subject.isNotBlank()) {
                    add("subject=${URLEncoder.encode(subject, enc)}")
                }
                if (body.isNotBlank()) {
                    add("body=${URLEncoder.encode(body, enc)}")
                }
            }.joinToString("&")
            val base = "mailto:${to.trim()}"
            return if (q.isBlank()) base else "$base?$q"
        }
    }
}
