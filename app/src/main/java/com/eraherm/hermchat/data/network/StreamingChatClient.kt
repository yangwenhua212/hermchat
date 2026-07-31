package com.eraherm.hermchat.data.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface StreamingChatClient {
    val connected: StateFlow<Boolean>
    suspend fun ensureConnected()
    fun streamChat(prompt: String): Flow<String>
    fun sendToolResult(toolCallId: String, ok: Boolean, message: String) {}
    fun close()
}
