package com.eraherm.hermchat.data.model

enum class AgentKind(
    val label: String,
    val defaultEndpoint: String,
    val defaultName: String,
) {
    WEBSOCKET(
        label = "WebSocket",
        defaultEndpoint = "ws://10.0.2.2:8765/ws",
        defaultName = "我的助手",
    ),
    HTTP_COMPAT(
        label = "HTTP 兼容",
        defaultEndpoint = "http://10.0.2.2:5000",
        defaultName = "我的助手",
    ),
    CUSTOM(
        label = "自定义",
        defaultEndpoint = "ws://",
        defaultName = "我的 Agent",
    ),
    ;

    companion object {
        fun fromStored(raw: String): AgentKind = when (raw) {
            "HERMES", "WEBSOCKET" -> WEBSOCKET
            "OPENCLAW", "HTTP_COMPAT" -> HTTP_COMPAT
            "CUSTOM" -> CUSTOM
            else -> runCatching { valueOf(raw) }.getOrDefault(CUSTOM)
        }
    }
}

data class AgentProfile(
    val id: String,
    val kind: AgentKind,
    val name: String,
    val endpoint: String,
    val createdAt: Long = System.currentTimeMillis(),
)
