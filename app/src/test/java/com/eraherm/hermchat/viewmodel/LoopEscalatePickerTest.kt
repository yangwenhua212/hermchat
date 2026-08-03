package com.eraherm.hermchat.viewmodel

import com.eraherm.hermchat.data.model.AgentKind
import com.eraherm.hermchat.data.model.AgentProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LoopEscalatePickerTest {

    private fun agent(
        id: String,
        kind: AgentKind,
        fallback: String? = null,
    ) = AgentProfile(
        id = id,
        kind = kind,
        name = id,
        endpoint = "http://x",
        fallbackAgentId = fallback,
    )

    @Test
    fun prefersExplicitFallback() {
        val gw = agent("gw", AgentKind.GATEWAY, fallback = "h1")
        val h1 = agent("h1", AgentKind.HERMES)
        val h2 = agent("h2", AgentKind.HERMES)
        assertEquals(h1, LoopEscalatePicker.pick(gw, listOf(gw, h2, h1)))
    }

    @Test
    fun picksFirstOtherRemote() {
        val gw = agent("gw", AgentKind.GATEWAY)
        val ws = agent("ws", AgentKind.WEBSOCKET)
        assertEquals(ws, LoopEscalatePicker.pick(gw, listOf(gw, ws)))
    }

    @Test
    fun nullWhenNoRemote() {
        val gw = agent("gw", AgentKind.GATEWAY)
        val api = agent("api", AgentKind.HTTP_COMPAT)
        assertNull(LoopEscalatePicker.pick(gw, listOf(gw, api)))
    }
}
