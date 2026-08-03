package com.eraherm.hermchat.data.network

import com.eraherm.hermchat.data.model.AgentKind
import com.eraherm.hermchat.data.model.AgentProfile

/**
 * Application 级 Agent 连接：不随 ChatViewModel / Activity 销毁而关掉，
 * 避免「退出聊天页 / 回桌面」就断线。换 Agent 或主动关闭时再 [release]。
 */
class AgentSessionHolder {
    @Volatile
    var client: StreamingChatClient? = null
        private set

    @Volatile
    var agentId: String? = null
        private set

    @Volatile
    var agentKind: AgentKind? = null
        private set

    fun matches(agent: AgentProfile): Boolean =
        client != null && agentId == agent.id

    fun attach(agent: AgentProfile, created: StreamingChatClient) {
        client = created
        agentId = agent.id
        agentKind = agent.kind
    }

    fun release(close: Boolean = true) {
        val old = client
        client = null
        agentId = null
        agentKind = null
        if (close) {
            runCatching { old?.close() }
        }
    }

    fun needsKeepAlive(): Boolean {
        val c = client ?: return false
        val kind = agentKind
        return kind == AgentKind.WEBSOCKET || kind == AgentKind.CUSTOM ||
            (kind == AgentKind.HERMES && c.connected.value)
    }
}
