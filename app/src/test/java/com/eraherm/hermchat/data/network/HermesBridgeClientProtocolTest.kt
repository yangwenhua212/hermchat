package com.eraherm.hermchat.data.network

import org.junit.Assert.assertEquals
import org.junit.Test

class HermesBridgeClientProtocolTest {

    @Test
    fun detectProtocol_apiWs() {
        assertEquals(
            WsProtocol.JSON_RPC,
            HermesBridgeClient.detectProtocol("ws://host:18789/api/ws"),
        )
    }

    @Test
    fun detectProtocol_v1Ws() {
        assertEquals(
            WsProtocol.AGENT_MESSAGE,
            HermesBridgeClient.detectProtocol("wss://host/v1/ws"),
        )
    }

    @Test
    fun detectProtocol_simple() {
        assertEquals(
            WsProtocol.SIMPLE,
            HermesBridgeClient.detectProtocol("ws://192.168.1.1:8765/ws"),
        )
    }
}
