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
) {
    fun merge(other: SetupAssistDraft): SetupAssistDraft = SetupAssistDraft(
        kind = other.kind ?: kind,
        endpoint = other.endpoint ?: endpoint,
        apiKey = other.apiKey ?: apiKey,
        model = other.model ?: model,
        name = other.name ?: name,
    )

    fun missingHints(): List<String> {
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
    /** 「连一下 47.x.x.x」口语 */
    private val LIAN_HOST = Regex(
        """连(?:一下|上|到)?\s*([^\s，,；;]{3,})""",
    )

    fun parse(raw: String): SetupAssistDraft {
        val text = raw.trim()
        if (text.isEmpty()) return SetupAssistDraft()

        val kind = detectKind(text)
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

        val endpoint = when {
            !url.isNullOrBlank() -> normalizeEndpoint(kind, url)
            !labeledHost.isNullOrBlank() -> normalizeEndpoint(kind, labeledHost)
            !lianHost.isNullOrBlank() -> normalizeEndpoint(kind, lianHost)
            ipMatch != null -> {
                val ip = ipMatch.groupValues[1]
                val port = ipMatch.groupValues.getOrNull(2).orEmpty()
                val host = if (port.isBlank()) ip else "$ip:$port"
                normalizeEndpoint(kind, host)
            }
            domainMatch != null && kind != AgentKind.WEBSOCKET -> {
                val host = domainMatch.groupValues[1]
                val port = domainMatch.groupValues.getOrNull(2).orEmpty()
                val bare = if (port.isBlank()) host else "$host:$port"
                normalizeEndpoint(kind, bare)
            }
            else -> null
        }

        val apiKey = SK_KEY.find(text)?.groupValues?.getOrNull(1)
            ?: LABELED_KEY.find(text)?.groupValues?.getOrNull(1)
                ?.let { ConnectionTester.sanitizeKey(it) }

        val model = LABELED_MODEL.find(text)?.groupValues?.getOrNull(1)
        val name = LABELED_NAME.find(text)?.groupValues?.getOrNull(1)

        return SetupAssistDraft(
            kind = kind,
            endpoint = endpoint,
            apiKey = apiKey,
            model = model,
            name = name,
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

    private fun detectKind(text: String): AgentKind? {
        val lower = text.lowercase()
        return when {
            lower.contains("ws://") || lower.contains("wss://") || lower.contains("websocket") ->
                AgentKind.WEBSOCKET
            lower.contains("本地") || lower.contains("local") -> AgentKind.LOCAL
            lower.contains("hermes") || lower.contains("我电脑上的") && lower.contains("助手") ->
                AgentKind.HERMES
            lower.contains("openai") || lower.contains("deepseek") || lower.contains("ollama") ||
                lower.contains("http兼容") || lower.contains("http 兼容") -> AgentKind.HTTP_COMPAT
            lower.contains("http://") || lower.contains("https://") -> AgentKind.HTTP_COMPAT
            // 「连一下 47…」未点名类型 → 默认 Hermes HTTP
            lower.contains("连一下") || lower.contains("连上") || lower.contains("连到") ->
                AgentKind.HERMES
            else -> null
        }
    }

    private fun normalizeEndpoint(kind: AgentKind?, raw: String): String {
        val t = raw.trim().trimEnd('/', ',', '，')
        return when {
            t.startsWith("ws://", true) || t.startsWith("wss://", true) -> t
            kind == AgentKind.HTTP_COMPAT || kind == AgentKind.HERMES || kind == null ->
                runCatching { HermesEndpoint.normalize(t) }.getOrDefault(t)
            else -> t
        }
    }
}
