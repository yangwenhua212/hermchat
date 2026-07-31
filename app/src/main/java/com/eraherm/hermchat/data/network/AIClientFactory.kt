package com.eraherm.hermchat.data.network

import com.eraherm.hermchat.data.model.AgentKind
import com.eraherm.hermchat.data.model.AgentProfile

object AIClientFactory {
    fun create(agent: AgentProfile): StreamingChatClient {
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
