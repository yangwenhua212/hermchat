package com.eraherm.hermchat.data.network

import com.eraherm.hermchat.data.model.AgentKind
import com.eraherm.hermchat.data.model.AgentProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionDegradePickerTest {
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
    fun remotePrefersGatewayThenHttpThenLocal() {
        val cur = agent("ws", AgentKind.WEBSOCKET)
        val local = agent("l", AgentKind.LOCAL)
        val http = agent("h", AgentKind.HTTP_COMPAT)
        val gw = agent("g", AgentKind.GATEWAY)
        assertEquals(gw, ConnectionDegradePicker.pick(cur, listOf(cur, local, http, gw)))
        assertEquals(http, ConnectionDegradePicker.pick(cur, listOf(cur, local, http)))
        assertEquals(local, ConnectionDegradePicker.pick(cur, listOf(cur, local)))
    }

    @Test
    fun explicitFallbackWins() {
        val cur = agent("ws", AgentKind.WEBSOCKET, fallback = "h")
        val gw = agent("g", AgentKind.GATEWAY)
        val http = agent("h", AgentKind.HTTP_COMPAT)
        assertEquals(http, ConnectionDegradePicker.pick(cur, listOf(cur, gw, http)))
    }

    @Test
    fun notFromGateway() {
        val cur = agent("g", AgentKind.GATEWAY)
        val http = agent("h", AgentKind.HTTP_COMPAT)
        assertNull(ConnectionDegradePicker.pick(cur, listOf(cur, http)))
    }

    @Test
    fun isRemotePrimary() {
        assertTrue(ConnectionDegradePicker.isRemotePrimary(agent("w", AgentKind.WEBSOCKET)))
        assertTrue(ConnectionDegradePicker.isRemotePrimary(agent("h", AgentKind.HERMES)))
        assertFalse(ConnectionDegradePicker.isRemotePrimary(agent("g", AgentKind.GATEWAY)))
    }
}
