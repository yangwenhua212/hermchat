package com.eraherm.hermchat

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eraherm.hermchat.data.local.DeviceCapability
import com.eraherm.hermchat.data.network.AgentConfigImport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmokeInstrumentedTest {

    @Test
    fun packageName() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.eraherm.hermchat", context.packageName)
    }

    @Test
    fun memorySnapshotReadable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val snap = DeviceCapability.memorySnapshot(context)
        assertTrue(snap.totalMb > 0)
    }

    @Test
    fun importConfigOnDevice() {
        val cfg = AgentConfigImport.parse("wss://example.com/ws").getOrThrow()
        assertEquals("wss://example.com/ws", cfg.endpoint)
    }
}
