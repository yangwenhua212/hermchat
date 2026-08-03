package com.eraherm.hermchat.data.local

import com.eraherm.hermchat.data.model.Message
import com.eraherm.hermchat.data.model.MessageRole
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class MessageRepository(
    private val dao: MessageDao,
    private val conversations: ConversationRepository,
) {
    fun observeMessages(): Flow<List<Message>> =
        conversations.activeId.flatMapLatest { id ->
            if (id == null) {
                flowOf(emptyList())
            } else {
                dao.observeByConversation(id).map { list -> list.map { it.toModel() } }
            }
        }

    suspend fun save(message: Message) {
        val conversationId = message.conversationId.ifBlank {
            conversations.activeId.value ?: return
        }
        dao.upsert(message.copy(conversationId = conversationId).toEntity())
        val titleHint = if (message.role == MessageRole.USER) message.content else null
        conversations.touch(conversationId, titleHint)
    }

    suspend fun clearActive() {
        val id = conversations.activeId.value ?: return
        dao.clearConversation(id)
    }

    suspend fun deleteConversationMessages(conversationId: String) {
        dao.clearConversation(conversationId)
    }

    suspend fun count(): Int {
        val id = conversations.activeId.value ?: return 0
        return dao.countIn(id)
    }

    suspend fun isEmpty(): Boolean = count() == 0

    /** 最近若干条，按时间正序（旧→新），供 HTTP 兼容客户端带上下文。 */
    suspend fun recentChronological(limit: Int): List<Message> {
        val id = conversations.activeId.value ?: return emptyList()
        return dao.recentDesc(id, limit).asReversed().map { it.toModel() }
    }
}
