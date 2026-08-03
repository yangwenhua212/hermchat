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
    val conversationId: String = "",
    /** 本地附件绝对路径（图片或文本副本） */
    val attachmentPath: String? = null,
    val attachmentMime: String? = null,
    val attachmentName: String? = null,
)
