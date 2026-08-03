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
    fun parse_bareSkKeyWithoutLabel() {
        val draft = SetupAssistParser.parse("sk-abc1234567890xyz")
        assertEquals("sk-abc1234567890xyz", draft.apiKey)
    }

    @Test
    fun parse_bareMixedKeyWithoutLabel() {
        // 非 sk- 前缀：整段粘贴、含字母+数字、足够长
        val key = "HermesTok9a8b7c6d5e4f3g2h1"
        val draft = SetupAssistParser.parse(key)
        assertEquals(key, draft.apiKey)
    }

    @Test
    fun parse_bareKey_ignoresModelName() {
        val draft = SetupAssistParser.parse("deepseek-chat")
        assertNull(draft.apiKey)
    }

    @Test
    fun parse_bareKey_ignoresHostOnly() {
        val draft = SetupAssistParser.parse("47.250.124.133")
        assertNull(draft.apiKey)
        assertEquals("http://47.250.124.133", draft.endpoint)
    }

    @Test
    fun merge_bareKeyAfterHost() {
        val a = SetupAssistParser.parse("连一下 47.250.124.133")
        val b = SetupAssistParser.parse("sk-nolebel123456789")
        val m = a.merge(b)
        assertEquals("http://47.250.124.133", m.endpoint)
        assertEquals("sk-nolebel123456789", m.apiKey)
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

    @Test
    fun parse_localReadyWithoutHost() {
        val draft = SetupAssistParser.parse("本地")
        assertEquals(AgentKind.LOCAL, draft.kind)
        assertEquals(AgentKind.LOCAL.defaultEndpoint, draft.endpoint)
        assertTrue(draft.isReadyToConnect())
        assertFalse(draft.wantsLanProbe)
    }

    @Test
    fun parse_gatewayNotLanProbe() {
        val draft = SetupAssistParser.parse("端侧网关")
        assertEquals(AgentKind.GATEWAY, draft.kind)
        assertEquals("https://api.deepseek.com", draft.endpoint)
        assertFalse(draft.wantsLanProbe)
    }

    @Test
    fun parse_httpCompatPhrase() {
        val draft = SetupAssistParser.parse("http 兼容")
        assertEquals(AgentKind.HTTP_COMPAT, draft.kind)
        assertFalse(draft.wantsLanProbe)
    }

    @Test
    fun summarize_local() {
        val draft = SetupAssistDraft(kind = AgentKind.LOCAL, endpoint = AgentKind.LOCAL.defaultEndpoint)
        val text = SetupAssistParser.summarizeForConfirm(draft)
        assertTrue(text.contains("本地"))
        assertTrue(text.contains("资源库") || text.contains("确认"))
    }
}
