package com.eraherm.hermchat.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HermesEndpointTest {
    @Test
    fun normalize_bareHost() {
        assertEquals("http://47.250.124.133", HermesEndpoint.normalize("47.250.124.133"))
        assertEquals("http://host.example:8080", HermesEndpoint.normalize("host.example:8080"))
    }

    @Test
    fun normalize_keepsHttp() {
        assertEquals("http://a.b", HermesEndpoint.normalize("http://a.b/"))
        assertEquals("https://a.b", HermesEndpoint.normalize("https://a.b"))
    }

    @Test
    fun normalize_rejectsWs() {
        assertThrows(IllegalStateException::class.java) {
            HermesEndpoint.normalize("ws://10.0.0.1:8765/ws")
        }
    }

    @Test
    fun normalize_rejectsBlank() {
        assertThrows(IllegalArgumentException::class.java) {
            HermesEndpoint.normalize("  ")
        }
    }
}
