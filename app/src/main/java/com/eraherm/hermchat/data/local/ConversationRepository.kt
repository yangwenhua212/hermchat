package com.eraherm.hermchat.data.local

import android.content.Context
import com.eraherm.hermchat.data.model.Conversation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

class ConversationRepository(
    context: Context,
    private val dao: ConversationDao,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _activeId = MutableStateFlow(prefs.getString(KEY_ACTIVE, null))
    val activeId: StateFlow<String?> = _activeId.asStateFlow()

    fun observeConversations(): Flow<List<Conversation>> =
        dao.observeAll().map { list -> list.map { it.toModel() } }

    fun observeForAgent(agentId: String): Flow<List<Conversation>> =
        dao.observeForAgent(agentId).map { list -> list.map { it.toModel() } }

    fun requireActiveId(): String =
        _activeId.value ?: error("没有当前会话")

    suspend fun bootstrap(agentId: String?): String {
        val current = _activeId.value
        if (current != null) {
            val row = dao.get(current)
            if (row != null &&
                (agentId.isNullOrBlank() || row.agentId == null || row.agentId == agentId)
            ) {
                return current
            }
        }
        if (!agentId.isNullOrBlank()) {
            val forAgent = dao.latestForAgent(agentId)
            if (forAgent != null) {
                setActive(forAgent.id)
                return forAgent.id
            }
        }
        val latest = dao.latest()
        if (latest != null && (agentId.isNullOrBlank() || latest.agentId == null || latest.agentId == agentId)) {
            setActive(latest.id)
            return latest.id
        }
        return createNew(agentId)
    }

    suspend fun createNew(agentId: String?): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        dao.upsert(
            ConversationEntity(
                id = id,
                title = DEFAULT_TITLE,
                agentId = agentId,
                updatedAt = now,
            ),
        )
        setActive(id)
        return id
    }

    /** 切换 Agent：优先回到该 Agent 最近一次会话，否则新建。 */
    suspend fun activateForAgent(agentId: String): String {
        val existing = dao.latestForAgent(agentId)
        if (existing != null) {
            setActive(existing.id)
            return existing.id
        }
        return createNew(agentId)
    }

    /** 迁移/首次启动的会话 agentId 可能为空，绑定时补上。 */
    suspend fun stampActiveAgent(agentId: String) {
        val id = _activeId.value ?: return
        val existing = dao.get(id) ?: return
        if (existing.agentId == null) {
            dao.upsert(existing.copy(agentId = agentId))
        }
    }

    /** 把尚无归属的旧会话划给该 Agent（仅一次迁移用）。 */
    suspend fun claimOrphanConversations(agentId: String) {
        dao.claimOrphans(agentId)
    }

    suspend fun setActive(id: String) {
        if (dao.get(id) == null) return
        prefs.edit().putString(KEY_ACTIVE, id).apply()
        _activeId.value = id
    }

    suspend fun touch(id: String, titleHint: String? = null) {
        val now = System.currentTimeMillis()
        val hint = titleHint?.trim().orEmpty()
        if (hint.isNotEmpty()) {
            val existing = dao.get(id)
            if (existing != null && existing.title == DEFAULT_TITLE) {
                dao.updateTitle(id, hint.take(MAX_TITLE), now)
                return
            }
        }
        dao.touch(id, now)
    }

    suspend fun delete(id: String, preferAgentId: String? = null) {
        val ownedAgent = dao.get(id)?.agentId
        dao.delete(id)
        if (_activeId.value == id) {
            val agentKey = preferAgentId ?: ownedAgent
            val next = agentKey?.let { dao.latestForAgent(it) } ?: dao.latest()
            if (next != null) {
                setActive(next.id)
            } else {
                prefs.edit().remove(KEY_ACTIVE).apply()
                _activeId.value = null
            }
        }
    }

    companion object {
        private const val PREFS = "hermchat_conversations"
        private const val KEY_ACTIVE = "active_id"
        const val DEFAULT_TITLE = "新对话"
        private const val MAX_TITLE = 28
    }
}

fun ConversationEntity.toModel(): Conversation = Conversation(
    id = id,
    title = title,
    agentId = agentId,
    updatedAt = updatedAt,
)
