package com.eraherm.hermchat.data.network

import android.content.Context
import com.eraherm.hermchat.data.local.LocalModelStore
import com.eraherm.hermchat.data.model.AgentKind
import com.eraherm.hermchat.data.model.AgentProfile

object AIClientFactory {
    fun create(agent: AgentProfile, context: Context? = null): StreamingChatClient {
        if (agent.kind == AgentKind.LOCAL ||
            agent.endpoint.startsWith("local://", ignoreCase = true)
        ) {
            val appContext = context?.applicationContext
                ?: error("本地运行时需要 Context")
            return LocalRuntimeClient(
                context = appContext,
                modelId = agent.model.ifBlank { LocalModelStore.DEFAULT_MODEL_ID }
                    .let { id ->
                        if (id == "default") LocalModelStore.DEFAULT_MODEL_ID else id
                    },
                hfToken = agent.apiKey,
            )
        }

        if (agent.kind == AgentKind.GATEWAY) {
            val appContext = context?.applicationContext
                ?: error("端侧网关需要 Context")
            return HybridGatewayClient(
                context = appContext,
                apiBaseUrl = agent.endpoint.trim(),
                apiKey = agent.apiKey,
                apiModel = agent.model.ifBlank { "deepseek-chat" },
                localModelId = LocalModelStore.DEFAULT_MODEL_ID,
                hfToken = agent.apiKey,
            )
        }

        val endpoint = agent.endpoint.trim()
        return when {
            agent.kind == AgentKind.HERMES ||
                agent.kind == AgentKind.HTTP_COMPAT ||
                endpoint.startsWith("http://", ignoreCase = true) ||
                endpoint.startsWith("https://", ignoreCase = true) -> {
                val base = if (agent.kind == AgentKind.HERMES) {
                    HermesEndpoint.normalize(endpoint)
                } else {
                    endpoint
                }
                OpenAiCompatClient(
                    baseUrl = base,
                    apiKey = agent.apiKey,
                    model = agent.model.ifBlank { "default" },
                    localToolsEnabled = agent.localToolsEnabled,
                    hermesSessionMode = agent.kind == AgentKind.HERMES,
                )
            }

            agent.kind == AgentKind.WEBSOCKET -> HermesBridgeClient.forHermes(endpoint)

            else -> HermesBridgeClient(endpoint)
        }
    }
}
