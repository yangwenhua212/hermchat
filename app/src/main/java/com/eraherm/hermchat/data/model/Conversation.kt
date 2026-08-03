package com.eraherm.hermchat.data.model

data class Conversation(
    val id: String,
    val title: String,
    val agentId: String?,
    val updatedAt: Long,
)
