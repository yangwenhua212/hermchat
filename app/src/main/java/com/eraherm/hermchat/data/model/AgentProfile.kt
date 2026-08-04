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
    /**
     * Hermes HTTP（少填主机）。手动配置列表不再单独展示，避免与「HTTP 兼容」重复；
     * 配置助手 / 扫码 / 已保存档案仍可用。编辑旧 Hermes 时类型选择里会临时出现。
     */
    HERMES(
        label = "Hermes",
        defaultEndpoint = "",
        defaultName = "Hermes",
    ),
    HTTP_COMPAT(
        label = "HTTP 兼容",
        defaultEndpoint = "http://10.0.2.2:5000",
        defaultName = "我的助手",
    ),
    /**
     * ④ 端侧网关：本地小模型兜底 + 必要时打 API + 本机工具。
     * endpoint/apiKey/model 描述云端 API；本地权重按需下载。
     */
    GATEWAY(
        label = "端侧网关",
        defaultEndpoint = "https://api.deepseek.com",
        defaultName = "端侧网关",
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
            "WEBSOCKET", "WS" -> WEBSOCKET
            "HERMES" -> HERMES
            "OPENCLAW", "HTTP_COMPAT", "HTTP", "OPENAI" -> HTTP_COMPAT
            "GATEWAY", "HYBRID", "AGENT_GATEWAY", "EDGE_GATEWAY" -> GATEWAY
            "LOCAL", "ONDEVICE", "ON_DEVICE" -> LOCAL
            "CUSTOM" -> CUSTOM
            else -> runCatching { valueOf(raw.trim().uppercase()) }.getOrDefault(CUSTOM)
        }

        /**
         * 旧扫码若写 kind=HERMES 但 endpoint 是 ws://，按地址兜底为 WebSocket。
         */
        fun resolve(raw: String, endpoint: String): AgentKind {
            val kind = fromStored(raw)
            val ep = endpoint.trim()
            if (raw.trim().equals("HERMES", ignoreCase = true) &&
                (ep.startsWith("ws://", ignoreCase = true) || ep.startsWith("wss://", ignoreCase = true))
            ) {
                return WEBSOCKET
            }
            return kind
        }

        /** 手动配置「选择类型」展示列表；默认不列 Hermes（与 HTTP 重复）。 */
        fun forManualPicker(current: AgentKind): List<AgentKind> =
            entries.filter { it != HERMES || current == HERMES }
    }
}

data class AgentProfile(
    val id: String,
    val kind: AgentKind,
    val name: String,
    val endpoint: String,
    val apiKey: String = "",
    /** LOCAL：端侧权重 id；GATEWAY/HTTP：云端 API 模型名。 */
    val model: String = "default",
    /**
     * GATEWAY 本地兜底权重 id；空则用默认 Qwen2.5 0.5B（免令牌目录）。
     * LOCAL 忽略此字段（用 [model]）。
     */
    val localModelId: String = "",
    /**
     * 远程大脑（HTTP/Hermes）返回的 tool_call 由本机 ToolRegistry 执行。
     * WebSocket / 本地默认同开。
     */
    val localToolsEnabled: Boolean = true,
    /** 显式指定故障备用 Agent；空则本轮 AgentFailover / 持久降级按档位挑。 */
    val fallbackAgentId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
