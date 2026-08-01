package com.eraherm.hermchat.data.network

import com.eraherm.hermchat.data.model.AgentKind
import com.eraherm.hermchat.data.model.AgentProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgentFailoverTest {
    private fun agent(
        id: String,
        kind: AgentKind,
        fallback: String? = null,
    ) = AgentProfile(
        id = id,
        kind = kind,
        name = id,
        endpoint = "https://example.com",
        fallbackAgentId = fallback,
    )

    @Test
    fun gatewayPrefersWebsocket() {
        val cur = agent("g", AgentKind.GATEWAY)
        val ws = agent("ws", AgentKind.WEBSOCKET)
        val http = agent("h", AgentKind.HTTP_COMPAT)
        assertEquals(ws, AgentFailover.pick(cur, listOf(cur, http, ws)))
    }

    @Test
    fun remotePrefersGateway() {
        val cur = agent("ws", AgentKind.WEBSOCKET)
        val gw = agent("g", AgentKind.GATEWAY)
        assertEquals(gw, AgentFailover.pick(cur, listOf(cur, gw)))
    }

    @Test
    fun explicitFallbackWins() {
        val cur = agent("g", AgentKind.GATEWAY, fallback = "h")
        val ws = agent("ws", AgentKind.WEBSOCKET)
        val http = agent("h", AgentKind.HTTP_COMPAT)
        assertEquals(http, AgentFailover.pick(cur, listOf(cur, ws, http)))
    }

    @Test
    fun noneWhenAlone() {
        val cur = agent("g", AgentKind.GATEWAY)
        assertNull(AgentFailover.pick(cur, listOf(cur)))
    }
}
