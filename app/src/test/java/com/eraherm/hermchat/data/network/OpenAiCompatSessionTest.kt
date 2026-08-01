package com.eraherm.hermchat.data.network

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiCompatSessionTest {
    @Test
    fun resetConversation_rotatesSessionId() {
        val a = OpenAiCompatClient.newSessionId()
        val b = OpenAiCompatClient.newSessionId()
        assertNotEquals(a, b)
        assertTrue(a.isNotBlank())
    }

    @Test
    fun sessionHeaderName_isHermes() {
        assertTrue(OpenAiCompatClient.HEADER_SESSION == "X-Hermes-Session-Id")
    }
}
