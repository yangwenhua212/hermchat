package com.eraherm.hermchat.data.network

import com.eraherm.hermchat.data.model.AgentKind
import com.eraherm.hermchat.data.model.AgentProfile

/** 一期识图：哪些 Agent 通道可尝试发 vision。 */
object VisionSupport {
    fun canSendImage(agent: AgentProfile?): Boolean {
        if (agent == null) return false
        return when (agent.kind) {
            AgentKind.HERMES, AgentKind.HTTP_COMPAT -> true
            AgentKind.WEBSOCKET, AgentKind.CUSTOM -> true
            AgentKind.GATEWAY -> {
                val ep = agent.endpoint.trim()
                ep.startsWith("http://", ignoreCase = true) ||
                    ep.startsWith("https://", ignoreCase = true)
            }
            AgentKind.LOCAL -> false
        }
    }

    /** 顶栏一行短提示，勿说教。 */
    fun unsupportedStatus(agent: AgentProfile?): String = when (agent?.kind) {
        AgentKind.LOCAL -> "本地模式不看图"
        AgentKind.GATEWAY -> "网关未配 API，无法识图"
        null -> "请先配置 Agent"
        else -> "当前模型可能不支持识图"
    }
}
