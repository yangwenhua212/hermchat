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

/** 打开地图搜索地点 / 地址（须确认）。 */
class MapsSearchTool(
    private val context: Context,
) : PhoneTool {
    override val name: String = NAME
    override val requiredPermissions: Array<String> = emptyArray()

    override suspend fun execute(call: ToolCall): ToolResult = withContext(Dispatchers.Main) {
        val query = call.arguments["query"]?.trim().orEmpty()
            .ifBlank { call.arguments["address"]?.trim().orEmpty() }
            .ifBlank { call.arguments["place"]?.trim().orEmpty() }
        if (query.isBlank()) {
            return@withContext ToolResult(call.id, name, false, "缺少地点")
        }
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
            .replace("+", "%20")
        val candidates = listOf(
            Uri.parse("geo:0,0?q=$encoded"),
            Uri.parse("https://maps.google.com/maps?q=$encoded"),
            Uri.parse("https://uri.amap.com/search?keyword=$encoded"),
        )
        return@withContext try {
            // Android 11+ resolveActivity 对 geo 常误判为 null，直接按序尝试
            var lastError: Exception? = null
            for (uri in candidates) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return@withContext ToolResult(call.id, name, true, "已打开地图「$query」")
                } catch (e: Exception) {
                    lastError = e
                }
            }
            ToolResult(call.id, name, false, lastError?.message ?: "无法打开地图")
        } catch (e: Exception) {
            ToolResult(call.id, name, false, e.message ?: "无法打开地图")
        }
    }

    companion object {
        const val NAME = "maps.search"
    }
}
