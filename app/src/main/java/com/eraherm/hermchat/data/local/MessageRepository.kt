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
}
