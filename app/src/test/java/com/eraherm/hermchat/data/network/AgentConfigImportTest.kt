package com.eraherm.hermchat.data.network

import com.eraherm.hermchat.data.model.AgentKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentConfigImportTest {

    @Test
    fun parseJson_websocket() {
        val raw = """
            {"v":1,"kind":"WEBSOCKET","endpoint":"ws://192.168.1.8:8765/ws","name":"家"}
        """.trimIndent()
        val cfg = AgentConfigImport.parse(raw).getOrThrow()
        assertEquals(AgentKind.WEBSOCKET, cfg.kind)
        assertEquals("ws://192.168.1.8:8765/ws", cfg.endpoint)
        assertEquals("家", cfg.name)
    }

    @Test
    fun parseJson_httpWithKey() {
        val raw = """
            {"kind":"HTTP_COMPAT","endpoint":"https://api.deepseek.com","apiKey":"sk-test","model":"deepseek-chat"}
        """.trimIndent()
        val cfg = AgentConfigImport.parse(raw).getOrThrow()
        assertEquals(AgentKind.HTTP_COMPAT, cfg.kind)
        assertEquals("sk-test", cfg.apiKey)
        assertEquals("deepseek-chat", cfg.model)
    }

    @Test
    fun parsePlainEndpoint() {
        val cfg = AgentConfigImport.parse("wss://bridge.example.com/ws").getOrThrow()
        assertEquals(AgentKind.WEBSOCKET, cfg.kind)
        assertEquals("wss://bridge.example.com/ws", cfg.endpoint)
    }

    @Test
    fun parseDeepLink() {
        val raw = "hxsync://agent?kind=HTTP_COMPAT&endpoint=https%3A%2F%2Fapi.openai.com&name=cloud"
        val cfg = AgentConfigImport.parse(raw).getOrThrow()
        assertEquals(AgentKind.HTTP_COMPAT, cfg.kind)
        assertEquals("https://api.openai.com", cfg.endpoint)
        assertEquals("cloud", cfg.name)
    }

    @Test
    fun parseJson_hermesHostOnly() {
        val raw = """
            {"kind":"HERMES","endpoint":"47.250.124.133","apiKey":"sk-x","name":"云"}
        """.trimIndent()
        val cfg = AgentConfigImport.parse(raw).getOrThrow()
        assertEquals(AgentKind.HERMES, cfg.kind)
        assertEquals("http://47.250.124.133", cfg.endpoint)
        assertEquals("sk-x", cfg.apiKey)
    }

    @Test
    fun parseJson_legacyHermesWsMapsToWebsocket() {
        val raw = """
            {"kind":"HERMES","endpoint":"ws://192.168.1.8:8765/ws","name":"旧"}
        """.trimIndent()
        val cfg = AgentConfigImport.parse(raw).getOrThrow()
        assertEquals(AgentKind.WEBSOCKET, cfg.kind)
        assertEquals("ws://192.168.1.8:8765/ws", cfg.endpoint)
    }

    @Test
    fun parseEmptyFails() {
        val result = AgentConfigImport.parse("   ")
        assertTrue(result.isFailure)
    }
}
