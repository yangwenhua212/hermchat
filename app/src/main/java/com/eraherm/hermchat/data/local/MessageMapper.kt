package com.eraherm.hermchat.data.local

import com.eraherm.hermchat.data.model.Message
import com.eraherm.hermchat.data.model.MessageRole

fun MessageEntity.toModel(): Message = Message(
    id = id,
    role = runCatching { MessageRole.valueOf(role) }.getOrDefault(MessageRole.ASSISTANT),
    content = content,
    providerLabel = providerLabel,
    createdAt = createdAt,
)

fun Message.toEntity(): MessageEntity = MessageEntity(
    id = id,
    role = role.name,
    content = content,
    providerLabel = providerLabel,
    createdAt = createdAt,
)
