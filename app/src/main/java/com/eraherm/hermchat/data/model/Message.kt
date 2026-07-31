package com.eraherm.hermchat.data.model

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
}

data class Message(
    val id: String,
    val role: MessageRole,
    val content: String,
    val providerLabel: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
