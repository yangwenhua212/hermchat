package com.eraherm.hermchat.data.network

import com.eraherm.hermchat.data.model.AgentKind
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder

/**
 * QR / 粘贴导入的 Agent 配置。
 *
 * 支持：
 * 1) JSON：`{"v":1,"kind":"WEBSOCKET","endpoint":"ws://…","name":"…"}`
 * 2) 深链：`hxsync://agent?kind=WEBSOCKET&endpoint=…&name=…`
 * 3) 纯地址：`ws://…` / `http://…`
 */
data class ImportedAgentConfig(
    val kind: AgentKind?,
    val endpoint: String,
    val name: String?,
)

object AgentConfigImport {
    fun parse(raw: String): Result<ImportedAgentConfig> = runCatching {
        val text = raw.trim()
        require(text.isNotEmpty()) { "内容为空" }

        when {
            text.startsWith("{") -> parseJson(text)
            text.startsWith("hxsync://", ignoreCase = true) ||
                text.startsWith("hermchat://", ignoreCase = true) -> parseDeepLink(text)
            looksLikeEndpoint(text) -> ImportedAgentConfig(
                kind = inferKind(text),
                endpoint = text,
                name = null,
            )
            else -> error("无法识别：请使用配置 JSON、hxsync:// 链接或 ws/http 地址")
        }
    }

    private fun parseJson(text: String): ImportedAgentConfig {
        val obj = JSONObject(text)
        val endpoint = obj.optString("endpoint").trim()
            .ifEmpty { obj.optString("url").trim() }
        require(endpoint.isNotEmpty()) { "JSON 缺少 endpoint" }
        require(looksLikeEndpoint(endpoint)) { "endpoint 格式不正确" }
        val kind = obj.optString("kind").takeIf { it.isNotBlank() }
            ?.let { AgentKind.fromStored(it) }
            ?: inferKind(endpoint)
        val name = obj.optString("name").takeIf { it.isNotBlank() }
        return ImportedAgentConfig(kind = kind, endpoint = endpoint, name = name)
    }

    private fun parseDeepLink(text: String): ImportedAgentConfig {
        val uri = URI(text)
        val query = uri.rawQuery ?: error("深链缺少参数")
        val params = query.split("&").associate { part ->
            val idx = part.indexOf('=')
            if (idx < 0) {
                part to ""
            } else {
                URLDecoder.decode(part.substring(0, idx), Charsets.UTF_8.name()) to
                    URLDecoder.decode(part.substring(idx + 1), Charsets.UTF_8.name())
            }
        }
        val endpoint = params["endpoint"]?.trim().orEmpty()
            .ifEmpty { params["url"]?.trim().orEmpty() }
        require(endpoint.isNotEmpty()) { "深链缺少 endpoint" }
        require(looksLikeEndpoint(endpoint)) { "endpoint 格式不正确" }
        val kind = params["kind"]?.takeIf { it.isNotBlank() }
            ?.let { AgentKind.fromStored(it) }
            ?: inferKind(endpoint)
        val name = params["name"]?.takeIf { it.isNotBlank() }
        return ImportedAgentConfig(kind = kind, endpoint = endpoint, name = name)
    }

    private fun looksLikeEndpoint(value: String): Boolean {
        val lower = value.lowercase()
        return lower.startsWith("ws://") ||
            lower.startsWith("wss://") ||
            lower.startsWith("http://") ||
            lower.startsWith("https://")
    }

    private fun inferKind(endpoint: String): AgentKind {
        val lower = endpoint.lowercase()
        return when {
            lower.startsWith("ws://") || lower.startsWith("wss://") -> AgentKind.WEBSOCKET
            lower.startsWith("http://") || lower.startsWith("https://") -> AgentKind.HTTP_COMPAT
            else -> AgentKind.CUSTOM
        }
    }
}
