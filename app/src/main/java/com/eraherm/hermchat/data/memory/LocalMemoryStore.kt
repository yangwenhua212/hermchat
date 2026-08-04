package com.eraherm.hermchat.data.memory

import com.eraherm.hermchat.data.local.ChatPrefsStore
import com.eraherm.hermchat.data.local.LocalMemoryDao
import com.eraherm.hermchat.data.local.LocalMemoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * ④ Agent loop 极简本机记忆：Room 存事实 + 关键词召回。
 * 不是 eraherm-memory；跨设备/语义向量仍走远端 Agent。
 */
class LocalMemoryStore(
    private val dao: LocalMemoryDao,
    private val prefsStore: ChatPrefsStore,
) {
    fun isReady(): Boolean = prefsStore.prefsFlow.value.memoryEnabled

    suspend fun formatRecallForPrompt(query: String, topK: Int = 5): String {
        if (!isReady()) return ""
        val items = recall(query, topK).getOrNull().orEmpty()
        if (items.isEmpty()) return ""
        return items.joinToString("\n") { "- ${it.content}" }
    }

    suspend fun recall(query: String, topK: Int = 5): Result<List<MemoryItem>> =
        withContext(Dispatchers.IO) {
            runCatching {
                check(isReady()) { "未开启本地记忆" }
                val q = query.trim()
                require(q.isNotBlank()) { "query 为空" }
                val all = dao.listAll()
                if (all.isEmpty()) return@runCatching emptyList()
                LocalMemoryRanker.rank(
                    query = q,
                    items = all,
                    topK = topK,
                    contentOf = { it.content },
                    pinnedOf = { it.pinned },
                    updatedAtOf = { it.updatedAt },
                ).map { scored ->
                    MemoryItem(
                        id = scored.item.id,
                        content = scored.item.content,
                        pinned = scored.item.pinned,
                        score = scored.score,
                    )
                }
            }
        }

    suspend fun remember(
        content: String,
        pinned: Boolean = false,
    ): Result<RememberResult> = withContext(Dispatchers.IO) {
        runCatching {
            check(isReady()) { "未开启本地记忆" }
            val text = content.trim()
            require(text.isNotBlank()) { "内容为空" }
            val capped = text.take(MAX_CONTENT)
            val now = System.currentTimeMillis()
            val existing = dao.findByContent(capped)
            val entity = if (existing != null) {
                existing.copy(
                    pinned = existing.pinned || pinned,
                    updatedAt = now,
                )
            } else {
                LocalMemoryEntity(
                    id = UUID.randomUUID().toString(),
                    content = capped,
                    pinned = pinned,
                    createdAt = now,
                    updatedAt = now,
                )
            }
            dao.upsert(entity)
            trimIfNeeded()
            RememberResult(id = entity.id, pinned = entity.pinned)
        }
    }

    private suspend fun trimIfNeeded() {
        val n = dao.count()
        if (n <= MAX_ITEMS) return
        dao.deleteOldestUnpinned(n - MAX_ITEMS)
    }

    data class MemoryItem(
        val id: String,
        val content: String,
        val pinned: Boolean,
        val score: Double,
    )

    data class RememberResult(
        val id: String,
        val pinned: Boolean,
    )

    companion object {
        const val MAX_ITEMS = 200
        const val MAX_CONTENT = 500
    }
}
