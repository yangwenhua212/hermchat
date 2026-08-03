package com.eraherm.hermchat.data.network

/** 发给 OpenAI 兼容 chat/completions 的单条消息（④ Agent loop 用）。 */
data class ApiChatMessage(
    val role: String,
    val content: String,
)
