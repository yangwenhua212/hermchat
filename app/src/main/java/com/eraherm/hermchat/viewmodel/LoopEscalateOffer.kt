package com.eraherm.hermchat.viewmodel

import com.eraherm.hermchat.data.model.AgentKind
import com.eraherm.hermchat.data.model.AgentProfile

/**
 * ④ Loop 超步数时挑选可一切换的远端 Agent（③）。
 * 有已保存的 Hermes/WebSocket 则返回目标；否则 null（UI 引导去添加）。
 */
object LoopEscalatePicker {
    fun pick(
        current: AgentProfile?,
        agents: List<AgentProfile>,
    ): AgentProfile? {
        val remotes = agents.filter {
            it.kind == AgentKind.HERMES || it.kind == AgentKind.WEBSOCKET
        }
        if (remotes.isEmpty()) return null

        current?.fallbackAgentId
            ?.takeIf { it.isNotBlank() }
            ?.let { id -> remotes.find { it.id == id } }
            ?.let { return it }

        return remotes.firstOrNull { it.id != current?.id } ?: remotes.firstOrNull()
    }
}

/** 聊天顶栏一键切 ③ / 去添加。 */
data class LoopEscalateOffer(
    val targetAgentId: String?,
    val targetName: String?,
) {
    val hasTarget: Boolean get() = !targetAgentId.isNullOrBlank()

    val actionLabel: String
        get() = if (hasTarget) {
            "切换到 ${targetName.orEmpty().ifBlank { "远端" }}"
        } else {
            "去添加"
        }
}
