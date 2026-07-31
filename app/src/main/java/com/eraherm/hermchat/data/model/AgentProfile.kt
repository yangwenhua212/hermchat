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
    LOCAL(
        label = "本地",
        defaultEndpoint = "local://runtime",
        defaultName = "本地助手",
    ),
    CUSTOM(
        label = "自定义",
        defaultEndpoint = "ws://",
        defaultName = "我的 Agent",
    ),
    ;

    companion object {
        fun fromStored(raw: String): AgentKind = when (raw.trim().uppercase()) {
            "HERMES", "WEBSOCKET", "WS" -> WEBSOCKET
            "OPENCLAW", "HTTP_COMPAT", "HTTP", "OPENAI" -> HTTP_COMPAT
            "LOCAL", "ONDEVICE", "ON_DEVICE" -> LOCAL
            "CUSTOM" -> CUSTOM
            else -> runCatching { valueOf(raw.trim().uppercase()) }.getOrDefault(CUSTOM)
        }
    }
}

data class AgentProfile(
    val id: String,
    val kind: AgentKind,
    val name: String,
    val endpoint: String,
    val apiKey: String = "",
    val model: String = "default",
    val createdAt: Long = System.currentTimeMillis(),
)
