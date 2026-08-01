package com.eraherm.hermchat.data.network

/**
 * Hermes 快捷连接：用户只填主机（IP/域名[/端口]），拼成 HTTP Base URL。
 */
object HermesEndpoint {
    fun normalize(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        require(trimmed.isNotEmpty()) { "主机不能为空" }
        val lower = trimmed.lowercase()
        return when {
            lower.startsWith("http://") || lower.startsWith("https://") -> trimmed
            lower.startsWith("ws://") || lower.startsWith("wss://") ->
                error("请填 HTTP 主机，不要用 ws://")
            else -> "http://$trimmed"
        }
    }
}
