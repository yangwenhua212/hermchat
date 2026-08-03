package com.eraherm.hermchat.tools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolRiskTest {

    @Test
    fun readOnly_skipsConfirm() {
        assertFalse(ToolRisk.READ_ONLY.requiresUserConfirm)
    }

    @Test
    fun writeAndDestructive_requireConfirm() {
        assertTrue(ToolRisk.WRITE.requiresUserConfirm)
        assertTrue(ToolRisk.DESTRUCTIVE.requiresUserConfirm)
    }
}
