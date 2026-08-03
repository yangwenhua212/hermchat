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

class WebSearchTool(
    private val context: Context,
) : PhoneTool {
    override val name: String = NAME
    override val requiredPermissions: Array<String> = emptyArray()

    override suspend fun execute(call: ToolCall): ToolResult = withContext(Dispatchers.Main) {
        val query = call.arguments["query"]?.trim().orEmpty()
        if (query.isBlank()) {
            return@withContext ToolResult(call.id, name, false, "缺少搜索词")
        }
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val uri = Uri.parse("https://www.bing.com/search?q=$encoded")
        return@withContext try {
            val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            ToolResult(call.id, name, true, "已打开搜索「$query」")
        } catch (e: Exception) {
            ToolResult(call.id, name, false, e.message ?: "无法打开搜索")
        }
    }

    companion object {
        const val NAME = "web.search"
    }
}
