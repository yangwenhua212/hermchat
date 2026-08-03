package com.eraherm.hermchat.data.network

/** 发给 OpenAI 兼容 chat/completions 的单条消息（④ Agent loop / vision）。 */
data class ApiChatMessage(
    val role: String,
    val content: String = "",
    /** `data:image/jpeg;base64,...`；有值时 content 序列化为多模态数组 */
    val imageDataUrl: String? = null,
) {
    fun hasPayload(): Boolean = content.isNotBlank() || !imageDataUrl.isNullOrBlank()
}
