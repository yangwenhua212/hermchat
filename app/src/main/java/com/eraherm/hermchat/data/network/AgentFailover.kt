package com.eraherm.hermchat.data.network

import com.eraherm.hermchat.data.model.AgentKind
import com.eraherm.hermchat.data.model.AgentProfile

/**
 * ④↔③ 等通道故障时自动挑选备用 Agent（本轮改用，不永久切换当前 Agent）。
 */
object AgentFailover {
    fun pick(
        current: AgentProfile?,
        agents: List<AgentProfile>,
    ): AgentProfile? {
        if (current == null) return null
        val others = agents.filter { it.id != current.id }
        if (others.isEmpty()) return null

        current.fallbackAgentId
            ?.takeIf { it.isNotBlank() }
            ?.let { id -> others.find { it.id == id } }
            ?.let { return it }

        return when (current.kind) {
            AgentKind.GATEWAY ->
                others.firstOrNull { it.kind == AgentKind.WEBSOCKET || it.kind == AgentKind.HERMES }
                    ?: others.firstOrNull { it.kind == AgentKind.HTTP_COMPAT }

            AgentKind.WEBSOCKET, AgentKind.HERMES ->
                others.firstOrNull { it.kind == AgentKind.GATEWAY }
                    ?: others.firstOrNull { it.kind == AgentKind.HTTP_COMPAT }

            AgentKind.HTTP_COMPAT ->
                others.firstOrNull { it.kind == AgentKind.GATEWAY }
                    ?: others.firstOrNull {
                        it.kind == AgentKind.WEBSOCKET || it.kind == AgentKind.HERMES
                    }

            AgentKind.LOCAL ->
                others.firstOrNull { it.kind == AgentKind.GATEWAY }
                    ?: others.firstOrNull {
                        it.kind == AgentKind.WEBSOCKET || it.kind == AgentKind.HERMES
                    }

            AgentKind.CUSTOM ->
                others.firstOrNull { it.kind == AgentKind.GATEWAY }
                    ?: others.firstOrNull { it.kind == AgentKind.WEBSOCKET }
        }
    }
}
