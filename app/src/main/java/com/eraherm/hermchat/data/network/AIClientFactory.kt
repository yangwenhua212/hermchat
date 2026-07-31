package com.eraherm.hermchat.data.network

import android.content.Context
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
                modelId = agent.model.ifBlank { "gemma3-1b-it-int4" }
                    .let { id ->
                        if (id == "default") "gemma3-1b-it-int4" else id
                    },
                hfToken = agent.apiKey,
            )
        }

        val endpoint = agent.endpoint.trim()
        return when {
            endpoint.startsWith("http://", ignoreCase = true) ||
                endpoint.startsWith("https://", ignoreCase = true) -> {
                OpenAiCompatClient(
                    baseUrl = endpoint,
                    apiKey = agent.apiKey,
                    model = agent.model.ifBlank { "default" },
                )
            }

            agent.kind == AgentKind.WEBSOCKET -> HermesBridgeClient.forHermes(endpoint)

            else -> HermesBridgeClient(endpoint)
        }
    }
}
