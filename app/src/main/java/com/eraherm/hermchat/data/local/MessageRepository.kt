package com.eraherm.hermchat.data.local

import com.eraherm.hermchat.data.model.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MessageRepository(
    private val dao: MessageDao,
) {
    fun observeMessages(): Flow<List<Message>> =
        dao.observeAll().map { list -> list.map { it.toModel() } }

    suspend fun save(message: Message) {
        dao.upsert(message.toEntity())
    }

    suspend fun clear() {
        dao.clear()
    }

    suspend fun count(): Int = dao.count()

    suspend fun isEmpty(): Boolean = dao.count() == 0

    /** 最近若干条，按时间正序（旧→新），供 HTTP 兼容客户端带上下文。 */
    suspend fun recentChronological(limit: Int): List<Message> =
        dao.recentDesc(limit).asReversed().map { it.toModel() }
}
