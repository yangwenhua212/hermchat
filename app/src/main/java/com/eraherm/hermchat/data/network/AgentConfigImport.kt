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
 * 4) Hermes：`kind=HERMES` + 主机（可无 scheme）
 */
data class ImportedAgentConfig(
    val kind: AgentKind?,
    val endpoint: String,
    val name: String?,
    val apiKey: String? = null,
    val model: String? = null,
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
        val rawEndpoint = obj.optString("endpoint").trim()
            .ifEmpty { obj.optString("url").trim() }
        require(rawEndpoint.isNotEmpty()) { "JSON 缺少 endpoint" }
        val kindHint = obj.optString("kind").takeIf { it.isNotBlank() }
        val kind = kindHint?.let { AgentKind.resolve(it, rawEndpoint) }
            ?: inferKind(rawEndpoint)
        val endpoint = normalizeImportedEndpoint(kind, rawEndpoint)
        require(looksLikeEndpoint(endpoint) || kind == AgentKind.HERMES) { "endpoint 格式不正确" }
        val name = obj.optString("name").takeIf { it.isNotBlank() }
        val apiKey = obj.optString("apiKey").takeIf { it.isNotBlank() }
            ?: obj.optString("api_key").takeIf { it.isNotBlank() }
        val model = obj.optString("model").takeIf { it.isNotBlank() }
        return ImportedAgentConfig(
            kind = kind,
            endpoint = endpoint,
            name = name,
            apiKey = apiKey,
            model = model,
        )
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
        val rawEndpoint = params["endpoint"]?.trim().orEmpty()
            .ifEmpty { params["url"]?.trim().orEmpty() }
        require(rawEndpoint.isNotEmpty()) { "深链缺少 endpoint" }
        val kindHint = params["kind"]?.takeIf { it.isNotBlank() }
        val kind = kindHint?.let { AgentKind.resolve(it, rawEndpoint) }
            ?: inferKind(rawEndpoint)
        val endpoint = normalizeImportedEndpoint(kind, rawEndpoint)
        require(looksLikeEndpoint(endpoint) || kind == AgentKind.HERMES) { "endpoint 格式不正确" }
        val name = params["name"]?.takeIf { it.isNotBlank() }
        val apiKey = params["apiKey"]?.takeIf { it.isNotBlank() }
            ?: params["api_key"]?.takeIf { it.isNotBlank() }
        val model = params["model"]?.takeIf { it.isNotBlank() }
        return ImportedAgentConfig(
            kind = kind,
            endpoint = endpoint,
            name = name,
            apiKey = apiKey,
            model = model,
        )
    }

    private fun normalizeImportedEndpoint(kind: AgentKind, raw: String): String {
        return if (kind == AgentKind.HERMES) {
            HermesEndpoint.normalize(raw)
        } else {
            raw.trim()
        }
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
