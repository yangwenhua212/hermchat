package com.eraherm.hermchat.data.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** 发给兼容 HTTP 客户端的短历史轮次。 */
data class ChatTurn(
    val role: String,
    val content: String,
)

interface StreamingChatClient {
    val connected: StateFlow<Boolean>
    suspend fun ensureConnected()
    fun streamChat(
        prompt: String,
        history: List<ChatTurn> = emptyList(),
    ): Flow<String>

    fun sendToolResult(toolCallId: String, ok: Boolean, message: String) {}

    /** 强制开启全新服务端会话（HTTP Hermes Session-Id / WS session.create）。 */
    fun resetConversation() {}

    fun close()
}
