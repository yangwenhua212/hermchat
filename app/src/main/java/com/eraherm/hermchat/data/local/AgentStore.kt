package com.eraherm.hermchat.data.local

import android.content.Context
import com.eraherm.hermchat.data.model.AgentKind
import com.eraherm.hermchat.data.model.AgentProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class AgentStore(
    context: Context,
) {
    private val prefs = SecurePrefs.open(
        context = context,
        secureName = PREFS_SECURE,
        plainName = PREFS_PLAIN,
        migrateKeys = listOf(KEY_AGENTS, KEY_CURRENT_ID),
    )

    private val _agents = MutableStateFlow(loadAgents())
    val agents: StateFlow<List<AgentProfile>> = _agents.asStateFlow()

    private val _currentId = MutableStateFlow(prefs.getString(KEY_CURRENT_ID, null))
    val currentId: StateFlow<String?> = _currentId.asStateFlow()

    val currentAgent: AgentProfile?
        get() {
            val id = _currentId.value ?: return _agents.value.firstOrNull()
            return _agents.value.find { it.id == id } ?: _agents.value.firstOrNull()
        }

    fun hasAgent(): Boolean = _agents.value.isNotEmpty()

    fun saveAgent(profile: AgentProfile, setCurrent: Boolean = true): AgentProfile {
        val existing = _agents.value.toMutableList()
        val index = existing.indexOfFirst { it.id == profile.id }
        if (index >= 0) {
            existing[index] = profile
        } else {
            existing.add(profile)
        }
        persist(existing)
        _agents.value = existing
        if (setCurrent) {
            setCurrentId(profile.id)
        }
        return profile
    }

    fun createDraft(
        kind: AgentKind,
        endpoint: String = kind.defaultEndpoint,
        name: String = kind.defaultName,
    ): AgentProfile = AgentProfile(
        id = UUID.randomUUID().toString(),
        kind = kind,
        name = name.trim().ifEmpty { kind.defaultName },
        endpoint = endpoint.trim(),
    )

    fun setCurrentId(id: String) {
        prefs.edit().putString(KEY_CURRENT_ID, id).apply()
        _currentId.value = id
    }

    fun removeAgent(id: String) {
        val remaining = _agents.value.filterNot { it.id == id }
        persist(remaining)
        _agents.value = remaining
        if (_currentId.value == id) {
            val next = remaining.firstOrNull()?.id
            if (next != null) {
                setCurrentId(next)
            } else {
                prefs.edit().remove(KEY_CURRENT_ID).apply()
                _currentId.value = null
            }
        }
    }

    fun refresh() {
        _agents.value = loadAgents()
        _currentId.value = prefs.getString(KEY_CURRENT_ID, null)
    }

    private fun persist(agents: List<AgentProfile>) {
        val array = JSONArray()
        agents.forEach { agent ->
            array.put(
                JSONObject()
                    .put("id", agent.id)
                    .put("kind", agent.kind.name)
                    .put("name", agent.name)
                    .put("endpoint", agent.endpoint)
                    .put("apiKey", agent.apiKey)
                    .put("model", agent.model)
                    .put("createdAt", agent.createdAt),
            )
        }
        prefs.edit().putString(KEY_AGENTS, array.toString()).apply()
    }

    private fun loadAgents(): List<AgentProfile> {
        val raw = prefs.getString(KEY_AGENTS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val kindRaw = obj.optString("kind", "CUSTOM")
                    val endpoint = obj.getString("endpoint")
                    val kind = AgentKind.resolve(kindRaw, endpoint)
                    add(
                        AgentProfile(
                            id = obj.getString("id"),
                            kind = kind,
                            name = obj.getString("name"),
                            endpoint = endpoint,
                            apiKey = obj.optString("apiKey", ""),
                            model = obj.optString("model", "default").ifBlank { "default" },
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val PREFS_SECURE = "hermchat_agents_secure"
        private const val PREFS_PLAIN = "hermchat_agents"
        private const val KEY_AGENTS = "agents_json"
        private const val KEY_CURRENT_ID = "current_agent_id"
    }
}
