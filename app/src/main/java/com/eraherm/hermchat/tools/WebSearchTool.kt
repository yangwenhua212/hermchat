package com.eraherm.hermchat.tools

import android.content.Context
import com.eraherm.hermchat.HermChatApp
import com.eraherm.hermchat.data.model.ToolCall
import com.eraherm.hermchat.data.model.ToolResult
import com.eraherm.hermchat.tools.search.SearchProviderException
import com.eraherm.hermchat.tools.search.WebSearchRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 本机联网搜索并回灌摘要；[ToolRisk.READ_ONLY]。
 * 源链：博查/Tavily（可选 key）→ SearXNG → DuckDuckGo。
 */
class WebSearchTool(
    private val context: Context,
) : PhoneTool {
    override val name: String = NAME
    override val requiredPermissions: Array<String> = emptyArray()
    override val risk: ToolRisk = ToolRisk.READ_ONLY

    override suspend fun execute(call: ToolCall): ToolResult = withContext(Dispatchers.IO) {
        val query = call.arguments["query"]?.trim().orEmpty()
            .ifBlank { call.arguments["q"]?.trim().orEmpty() }
            .ifBlank { call.arguments["text"]?.trim().orEmpty() }
        if (query.isBlank()) {
            return@withContext ToolResult(call.id, name, false, "缺少搜索词")
        }
        val prefs = (context.applicationContext as? HermChatApp)?.chatPrefsStore?.prefsFlow?.value
        return@withContext try {
            val outcome = WebSearchRouter.search(
                query = query,
                bochaKey = prefs?.bochaApiKey,
                tavilyKey = prefs?.tavilyApiKey,
            )
            val text = WebSearchRouter.formatHits(outcome.hits, outcome.providerId)
            val prefix = if (outcome.degraded) {
                "（已改用${WebSearchRouter.providerLabel(outcome.providerId)}）\n"
            } else {
                ""
            }
            ToolResult(call.id, name, true, prefix + text)
        } catch (e: SearchProviderException) {
            ToolResult(call.id, name, false, e.message ?: "搜索失败")
        } catch (e: Exception) {
            ToolResult(call.id, name, false, e.message ?: "搜索失败")
        }
    }

    companion object {
        const val NAME = "web.search"
    }
}
