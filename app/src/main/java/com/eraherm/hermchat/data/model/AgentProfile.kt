package com.eraherm.hermchat.data.model

enum class AgentKind(
    val label: String,
    val defaultEndpoint: String,
    val defaultName: String,
) {
    HERMES(
        label = "Hermes",
        // 简易 Bridge 用 /ws；官方 dashboard 网关多为 /api/ws
        defaultEndpoint = "ws://10.0.2.2:8765/ws",
        defaultName = "我的 Hermes",
    ),
    OPENCLAW(
        label = "OpenClaw",
        defaultEndpoint = "http://10.0.2.2:5000",
        defaultName = "我的 OpenClaw",
    ),
    CUSTOM(
        label = "自定义",
        defaultEndpoint = "ws://",
        defaultName = "我的 Agent",
    ),
}

data class AgentProfile(
    val id: String,
    val kind: AgentKind,
    val name: String,
    val endpoint: String,
    val createdAt: Long = System.currentTimeMillis(),
)
