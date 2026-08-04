package com.eraherm.hermchat.data.network

import com.eraherm.hermchat.data.model.AgentKind
import com.eraherm.hermchat.data.model.AgentProfile

/**
 * 可选「连接失败自动降级」：从 ③ 持久切到备选档。
 * 与 [AgentFailover]（本轮临时备用、不改 currentId）分离。
 *
 * 优先级：显式 `fallbackAgentId` → ④ GATEWAY → ② HTTP_COMPAT → ① LOCAL
 */
object ConnectionDegradePicker {
    fun isRemotePrimary(agent: AgentProfile?): Boolean =
        agent?.kind == AgentKind.WEBSOCKET ||
            agent?.kind == AgentKind.HERMES ||
            agent?.kind == AgentKind.CUSTOM

    fun pick(
        current: AgentProfile?,
        agents: List<AgentProfile>,
    ): AgentProfile? {
        if (current == null || !isRemotePrimary(current)) return null
        val others = agents.filter { it.id != current.id }
        if (others.isEmpty()) return null

        current.fallbackAgentId
            ?.takeIf { it.isNotBlank() }
            ?.let { id -> others.find { it.id == id } }
            ?.let { return it }

        return others.firstOrNull { it.kind == AgentKind.GATEWAY }
            ?: others.firstOrNull { it.kind == AgentKind.HTTP_COMPAT }
            ?: others.firstOrNull { it.kind == AgentKind.LOCAL }
    }
}
