package com.eraherm.hermchat.data.network

import com.eraherm.hermchat.data.model.AgentKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupAssistParserTest {
    @Test
    fun parse_hermesHostAndKey() {
        val draft = SetupAssistParser.parse(
            "Hermes 连 47.250.124.133 key是 sk-abc123456789 model是 default",
        )
        assertEquals(AgentKind.HERMES, draft.kind)
        assertEquals("http://47.250.124.133", draft.endpoint)
        assertEquals("sk-abc123456789", draft.apiKey)
        assertEquals("default", draft.model)
        assertTrue(draft.isReadyToConnect())
    }

    @Test
    fun parse_colloquialLianAndPassword() {
        val draft = SetupAssistParser.parse("连一下 47.250.124.133，密码是 sk-xyz987654321")
        assertEquals(AgentKind.HERMES, draft.kind)
        assertEquals("http://47.250.124.133", draft.endpoint)
        assertEquals("sk-xyz987654321", draft.apiKey)
    }

    @Test
    fun parse_pcHermesWebsocket() {
        val draft = SetupAssistParser.parse(
            "用我电脑上的 Hermes，地址是 ws://192.168.1.8:8765/ws",
        )
        assertEquals(AgentKind.WEBSOCKET, draft.kind)
        assertEquals("ws://192.168.1.8:8765/ws", draft.endpoint)
    }

    @Test
    fun parse_lanProbeIntent() {
        val draft = SetupAssistParser.parse("连电脑上的助手")
        assertEquals(AgentKind.WEBSOCKET, draft.kind)
        assertTrue(draft.wantsLanProbe)
        assertNull(draft.endpoint)
    }

    @Test
    fun parse_websocketHostNeedsProbe() {
        val draft = SetupAssistParser.parse("websocket 192.168.1.8")
        assertEquals(AgentKind.WEBSOCKET, draft.kind)
        assertEquals("192.168.1.8", draft.probeHost)
        assertNull(draft.endpoint)
    }

    @Test
    fun parse_websocketUrl() {
        val draft = SetupAssistParser.parse("websocket ws://192.168.1.8:8765/ws")
        assertEquals(AgentKind.WEBSOCKET, draft.kind)
        assertEquals("ws://192.168.1.8:8765/ws", draft.endpoint)
    }

    @Test
    fun merge_fillsMissing() {
        val a = SetupAssistParser.parse("Hermes 47.250.124.133")
        val b = SetupAssistParser.parse("key是 sk-zzzzzzzzzzzz")
        val m = a.merge(b)
        assertEquals("http://47.250.124.133", m.endpoint)
        assertEquals("sk-zzzzzzzzzzzz", m.apiKey)
        assertEquals(AgentKind.HERMES, m.kind)
    }

    @Test
    fun summarize_masksKey() {
        val draft = SetupAssistDraft(
            kind = AgentKind.HERMES,
            endpoint = "http://47.250.124.133",
            apiKey = "sk-abcdefghij",
        )
        val text = SetupAssistParser.summarizeForConfirm(draft)
        assertTrue(text.contains("47.250.124.133"))
        assertTrue(text.contains("确认用这个连接吗"))
        assertFalse(text.contains("sk-abcdefghij"))
        assertTrue(text.contains("sk-a…ij") || text.contains("…"))
    }
}
