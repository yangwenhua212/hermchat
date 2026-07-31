package com.eraherm.hermchat.data.network

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionTesterSanitizeTest {
    @Test
    fun sanitizeKey_stripsLeadingParenAndWhitespace() {
        assertEquals("sk-abc", ConnectionTester.sanitizeKey(")sk-abc"))
        assertEquals("sk-abc", ConnectionTester.sanitizeKey("  sk-abc  "))
        assertEquals("sk-abc", ConnectionTester.sanitizeKey(") sk-abc"))
    }
}
