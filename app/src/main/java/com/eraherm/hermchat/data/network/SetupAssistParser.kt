package com.eraherm.hermchat.data.network

import com.eraherm.hermchat.data.model.AgentKind

/**
 * 从自然语言 / 粘贴文本里提取 Agent 配置（本机规则，不调大模型）。
 */
data class SetupAssistDraft(
    val kind: AgentKind? = null,
    val endpoint: String? = null,
    val apiKey: String? = null,
    val model: String? = null,
    val name: String? = null,
    /** 用户想连电脑/局域网但还没给全地址 → 触发探测 */
    val wantsLanProbe: Boolean = false,
    /** 仅有主机，需在该主机上扫常见 WS/HTTP 端口 */
    val probeHost: String? = null,
) {
    fun merge(other: SetupAssistDraft): SetupAssistDraft = SetupAssistDraft(
        kind = other.kind ?: kind,
        endpoint = other.endpoint ?: endpoint,
        apiKey = other.apiKey ?: apiKey,
        model = other.model ?: model,
        name = other.name ?: name,
        wantsLanProbe = other.wantsLanProbe || wantsLanProbe,
        probeHost = other.probeHost ?: probeHost,
    )

    fun missingHints(): List<String> {
        if (wantsLanProbe || !probeHost.isNullOrBlank()) return emptyList()
        val resolvedKind = resolvedKind()
        return buildList {
            if (endpoint.isNullOrBlank()) {
                add(if (resolvedKind == AgentKind.WEBSOCKET) "WebSocket 地址" else "主机")
            }
        }
    }

    fun resolvedKind(): AgentKind {
        val ep = endpoint.orEmpty()
        return kind ?: when {
            ep.startsWith("ws://", true) || ep.startsWith("wss://", true) -> AgentKind.WEBSOCKET
            ep.startsWith("http://", true) || ep.startsWith("https://", true) -> AgentKind.HERMES
            wantsLanProbe -> AgentKind.WEBSOCKET
            else -> AgentKind.HERMES
        }
    }

    fun isReadyToConnect(): Boolean = !endpoint.isNullOrBlank()
}

object SetupAssistParser {
    private val IPV4 = Regex("""\b(\d{1,3}(?:\.\d{1,3}){3})(?::(\d{2,5}))?\b""")
    private val URL = Regex(
        """(?i)\b((?:https?|wss?)://[^\s，,；;]+)""",
    )
    private val HOST_PORT = Regex(
        """(?i)\b((?:[a-z0-9-]+\.)+[a-z]{2,})(?::(\d{2,5}))?\b""",
    )
    private val SK_KEY = Regex("""\b(sk-[A-Za-z0-9._\-]{8,})\b""")
    private val LABELED_KEY = Regex(
        """(?i)(?:api\s*key|apikey|密钥|密码|口令|token|key)\s*[=:：是为]?\s*([^\s，,；;]+)""",
    )
    private val LABELED_MODEL = Regex(
        """(?i)(?:model|模型)\s*[=:：是为]?\s*([^\s，,；;]+)""",
    )
    private val LABELED_HOST = Regex(
        """(?i)(?:主机|地址|host|endpoint|url)\s*[=:：是为]?\s*([^\s，,；;]+)""",
    )
    private val LABELED_NAME = Regex(
        """(?:名字|名称|叫)\s*[=:：是]?\s*[「"']?([^「"'\s，,；;]{1,20})""",
    )
    private val LIAN_HOST = Regex(
        """连(?:一下|上|到)?\s*([^\s，,；;]{3,})""",
    )

    fun parse(raw: String): SetupAssistDraft {
        val text = raw.trim()
        if (text.isEmpty()) return SetupAssistDraft()

        val kind = detectKind(text)
        val wantsLanProbe = detectLanProbeIntent(text)
        val url = URL.find(text)?.groupValues?.getOrNull(1)?.trimEnd(',', '，', ';', '；')
        val labeledHost = LABELED_HOST.find(text)?.groupValues?.getOrNull(1)
        val lianHost = LIAN_HOST.find(text)?.groupValues?.getOrNull(1)
            ?.takeIf { candidate ->
                IPV4.containsMatchIn(candidate) ||
                    candidate.contains("://") ||
                    HOST_PORT.containsMatchIn(candidate)
            }
        val ipMatch = IPV4.find(text)
        val domainMatch = HOST_PORT.find(text)?.takeIf {
            val host = it.groupValues[1].lowercase()
            !host.endsWith(".png") && host != "api.key" && !host.contains("example")
        }

        val rawHost = when {
            !url.isNullOrBlank() -> url
            !labeledHost.isNullOrBlank() -> labeledHost
            !lianHost.isNullOrBlank() -> lianHost
            ipMatch != null -> {
                val ip = ipMatch.groupValues[1]
                val port = ipMatch.groupValues.getOrNull(2).orEmpty()
                if (port.isBlank()) ip else "$ip:$port"
            }
            domainMatch != null -> {
                val host = domainMatch.groupValues[1]
                val port = domainMatch.groupValues.getOrNull(2).orEmpty()
                if (port.isBlank()) host else "$host:$port"
            }
            else -> null
        }

        val resolvedKind = kind ?: if (wantsLanProbe) AgentKind.WEBSOCKET else null
        val hasFullWs = rawHost?.startsWith("ws://", true) == true ||
            rawHost?.startsWith("wss://", true) == true
        val hasFullHttp = rawHost?.startsWith("http://", true) == true ||
            rawHost?.startsWith("https://", true) == true

        // WebSocket：完整 ws 地址直接用；仅 IP/主机则交给探测补全路径
        val endpoint: String?
        val probeHost: String?
        when {
            rawHost == null -> {
                endpoint = null
                probeHost = null
            }
            hasFullWs || (resolvedKind != AgentKind.WEBSOCKET && hasFullHttp) -> {
                endpoint = normalizeEndpoint(resolvedKind, rawHost)
                probeHost = null
            }
            resolvedKind == AgentKind.WEBSOCKET || wantsLanProbe -> {
                // 例如「websocket 192.168.1.8」→ 对该主机扫常见端口
                endpoint = null
                probeHost = stripToHost(rawHost)
            }
            else -> {
                endpoint = normalizeEndpoint(resolvedKind, rawHost)
                probeHost = null
            }
        }

        val apiKey = SK_KEY.find(text)?.groupValues?.getOrNull(1)
            ?: LABELED_KEY.find(text)?.groupValues?.getOrNull(1)
                ?.let { ConnectionTester.sanitizeKey(it) }

        val model = LABELED_MODEL.find(text)?.groupValues?.getOrNull(1)
        val name = LABELED_NAME.find(text)?.groupValues?.getOrNull(1)

        return SetupAssistDraft(
            kind = resolvedKind ?: kind,
            endpoint = endpoint,
            apiKey = apiKey,
            model = model,
            name = name,
            wantsLanProbe = wantsLanProbe && endpoint == null && probeHost == null,
            probeHost = probeHost,
        )
    }

    fun maskKey(key: String): String {
        val k = key.trim()
        if (k.isEmpty()) return "（无）"
        if (k.length <= 6) return "••••"
        return k.take(4) + "…" + k.takeLast(2)
    }

    fun summarizeForConfirm(draft: SetupAssistDraft): String {
        val kind = draft.resolvedKind()
        val ep = draft.endpoint.orEmpty()
        val keyPart = when (kind) {
            AgentKind.HERMES, AgentKind.HTTP_COMPAT ->
                "，Key ${maskKey(draft.apiKey.orEmpty())}"
            else -> ""
        }
        val modelPart = draft.model?.takeIf { it.isNotBlank() && it != "default" }
            ?.let { "，模型 $it" }
            .orEmpty()
        val typePart = when (kind) {
            AgentKind.HERMES -> "Hermes"
            AgentKind.WEBSOCKET -> "WebSocket"
            AgentKind.HTTP_COMPAT -> "HTTP"
            AgentKind.LOCAL -> "本地"
            AgentKind.CUSTOM -> "自定义"
        }
        return "我找到了：$typePart 地址 $ep$keyPart$modelPart。确认用这个连接吗？"
    }

    fun detectLanProbeIntent(text: String): Boolean {
        val lower = text.lowercase()
        val hints = listOf(
            "电脑", "局域网", "同一wifi", "同一 wi", "自动探测", "探测一下",
            "找一下", "搜一下", "bridge", "网关", "远程agent", "远程 agent",
            "连电脑", "家里的助手", "办公室",
        )
        if (hints.none { lower.contains(it) }) return false
        // 已给完整地址则不必再盲探
        if (URL.containsMatchIn(text)) return false
        return true
    }

    private fun stripToHost(raw: String): String {
        var t = raw.trim()
        t = t.removePrefix("http://").removePrefix("https://")
            .removePrefix("ws://").removePrefix("wss://")
        return t.substringBefore("/").trimEnd('/')
    }

    private fun detectKind(text: String): AgentKind? {
        val lower = text.lowercase()
        return when {
            lower.contains("ws://") || lower.contains("wss://") || lower.contains("websocket") ->
                AgentKind.WEBSOCKET
            lower.contains("bridge") || lower.contains("gateway") ||
                lower.contains("局域网") || lower.contains("连电脑") ||
                (lower.contains("电脑") && !lower.contains("http")) ->
                AgentKind.WEBSOCKET
            lower.contains("本地") || lower.contains("local") -> AgentKind.LOCAL
            lower.contains("hermes") -> AgentKind.HERMES
            lower.contains("openai") || lower.contains("deepseek") || lower.contains("ollama") ||
                lower.contains("http兼容") || lower.contains("http 兼容") -> AgentKind.HTTP_COMPAT
            lower.contains("http://") || lower.contains("https://") -> AgentKind.HTTP_COMPAT
            lower.contains("连一下") || lower.contains("连上") || lower.contains("连到") ->
                AgentKind.HERMES
            else -> null
        }
    }

    private fun normalizeEndpoint(kind: AgentKind?, raw: String): String {
        val t = raw.trim().trimEnd('/', ',', '，')
        return when {
            t.startsWith("ws://", true) || t.startsWith("wss://", true) -> t
            t.startsWith("http://", true) || t.startsWith("https://", true) -> t
            kind == AgentKind.WEBSOCKET -> {
                if (t.contains(":")) "ws://$t/ws" else "ws://$t:8765/ws"
            }
            kind == AgentKind.HTTP_COMPAT || kind == AgentKind.HERMES || kind == null ->
                runCatching { HermesEndpoint.normalize(t) }.getOrDefault(t)
            else -> t
        }
    }
}
