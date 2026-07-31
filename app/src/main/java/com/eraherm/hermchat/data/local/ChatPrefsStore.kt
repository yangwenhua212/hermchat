package com.eraherm.hermchat.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class InputMode(
    val label: String,
    val hint: String,
) {
    VOICE_FIRST("语音优先", "麦克风更醒目，适合边走边说"),
    TEXT_FIRST("文字优先", "进入聊天自动聚焦输入框"),
    MIXED("混合", "麦克风与键盘并重（默认）"),
}

enum class ShortcutAction {
    INSERT,
    SEND,
}

data class ShortcutDef(
    val id: String,
    val label: String,
    val text: String,
    val action: ShortcutAction,
)

data class ChatPrefs(
    val inputMode: InputMode = InputMode.MIXED,
    val shortcuts: List<ShortcutDef> = DEFAULT_SHORTCUTS,
) {
    companion object {
        val DEFAULT_SHORTCUTS = listOf(
            ShortcutDef("today", "今天日程", "今天有什么日程？", ShortcutAction.SEND),
            ShortcutDef("remind", "提醒我…", "提醒我", ShortcutAction.INSERT),
            ShortcutDef("meeting", "明天开会", "明天下午3点提醒我开会", ShortcutAction.INSERT),
            ShortcutDef("book", "预约…", "帮我预约", ShortcutAction.INSERT),
        )
    }
}

class ChatPrefsStore(
    context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _prefs = MutableStateFlow(load())
    val prefsFlow: StateFlow<ChatPrefs> = _prefs.asStateFlow()

    fun update(transform: (ChatPrefs) -> ChatPrefs) {
        val next = transform(_prefs.value)
        persist(next)
        _prefs.value = next
    }

    fun setInputMode(mode: InputMode) {
        update { it.copy(inputMode = mode) }
    }

    fun moveShortcut(id: String, offset: Int) {
        update { current ->
            val list = current.shortcuts.toMutableList()
            val index = list.indexOfFirst { it.id == id }
            if (index < 0) return@update current
            val target = (index + offset).coerceIn(0, list.lastIndex)
            if (target == index) return@update current
            val item = list.removeAt(index)
            list.add(target, item)
            current.copy(shortcuts = list)
        }
    }

    fun resetShortcuts() {
        update { it.copy(shortcuts = ChatPrefs.DEFAULT_SHORTCUTS) }
    }

    private fun persist(value: ChatPrefs) {
        val array = JSONArray()
        value.shortcuts.forEach { shortcut ->
            array.put(
                JSONObject()
                    .put("id", shortcut.id)
                    .put("label", shortcut.label)
                    .put("text", shortcut.text)
                    .put("action", shortcut.action.name),
            )
        }
        prefs.edit()
            .putString(KEY_INPUT_MODE, value.inputMode.name)
            .putString(KEY_SHORTCUTS, array.toString())
            .apply()
    }

    private fun load(): ChatPrefs {
        val mode = prefs.getString(KEY_INPUT_MODE, InputMode.MIXED.name)
            ?.let { raw ->
                runCatching { InputMode.valueOf(raw) }.getOrDefault(InputMode.MIXED)
            }
            ?: InputMode.MIXED
        val shortcuts = loadShortcuts()
        return ChatPrefs(inputMode = mode, shortcuts = shortcuts)
    }

    private fun loadShortcuts(): List<ShortcutDef> {
        val raw = prefs.getString(KEY_SHORTCUTS, null) ?: return ChatPrefs.DEFAULT_SHORTCUTS
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val action = runCatching {
                        ShortcutAction.valueOf(obj.getString("action"))
                    }.getOrDefault(ShortcutAction.INSERT)
                    add(
                        ShortcutDef(
                            id = obj.getString("id"),
                            label = obj.getString("label"),
                            text = obj.getString("text"),
                            action = action,
                        ),
                    )
                }
            }.ifEmpty { ChatPrefs.DEFAULT_SHORTCUTS }
        }.getOrDefault(ChatPrefs.DEFAULT_SHORTCUTS)
    }

    companion object {
        private const val PREFS = "hermchat_chat"
        private const val KEY_INPUT_MODE = "input_mode"
        private const val KEY_SHORTCUTS = "shortcuts"
    }
}
