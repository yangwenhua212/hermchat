package com.eraherm.hermchat.data.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface StreamingChatClient {
    val connected: StateFlow<Boolean>
    suspend fun ensureConnected()
    fun streamChat(prompt: String): Flow<String>
    fun sendToolResult(toolCallId: String, ok: Boolean, message: String) {}

    /** 强制开启全新服务端会话（HTTP Hermes Session-Id / WS session.create）。 */
    fun resetConversation() {}

    fun close()
}
