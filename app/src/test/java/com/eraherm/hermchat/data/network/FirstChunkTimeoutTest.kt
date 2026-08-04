package com.eraherm.hermchat.data.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FirstChunkTimeoutTest {
    @Test
    fun timeoutWhenNoChunk() = runBlocking {
        val stream = flow<String> {
            delay(5_000)
            emit("late")
        }
        try {
            stream.collectWithFirstChunkTimeout(200) { }
            fail("expected timeout")
        } catch (e: FirstChunkTimeoutException) {
            assertEquals(200, e.timeoutMs)
        }
    }

    @Test
    fun okWhenFirstChunkArrives() = runBlocking {
        val out = StringBuilder()
        val stream = flow {
            delay(50)
            emit("a")
            emit("b")
        }
        stream.collectWithFirstChunkTimeout(2_000) { out.append(it) }
        assertEquals("ab", out.toString())
    }

    @Test
    fun emptyCompleteIsNotTimeout() = runBlocking {
        val stream = flow<String> { }
        stream.collectWithFirstChunkTimeout(500) { fail("no emissions") }
        assertTrue(true)
    }
}
