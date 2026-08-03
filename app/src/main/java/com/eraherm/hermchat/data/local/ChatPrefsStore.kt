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

enum class ChatThemeStyle(val label: String) {
    FOREST("系统默认绿"),
    MIST("浅雾绿"),
    SKY("浅天蓝"),
    AQUA("浅青"),
    CLOUD("浅灰白"),
    APRICOT("浅杏"),
    INK("墨黑"),
    /** 整 App 深色 Material + 夜间背景，适合晚上 */
    NIGHT("深色夜间"),
}

enum class BubbleStyle(val label: String) {
    ROUND("圆润"),
    SOFT("柔和"),
    SQUARE("直角"),
}

enum class ChatBackgroundMode(val label: String) {
    THEME("跟随主题色"),
    IMAGE("自定义图片"),
}

/** 助手回复朗读引擎。 */
enum class SpeakEngine(val label: String) {
    SYSTEM("系统朗读"),
    /** 微软 Edge TTS，与 Hermes `tts.provider: edge` 同路（默认小艺） */
    EDGE("Edge 小艺"),
    /** OpenAI 兼容 `/v1/audio/speech`，需自填 TTS 地址 */
    REMOTE("自定义 TTS"),
    AUTO("自动"),
}

/** ④ 端侧网关路由：自动判复杂度，或手选优先本地 / 云端。 */
enum class GatewayRouteMode(val label: String) {
    AUTO("自动"),
    LOCAL("优先本地"),
    API("优先云端"),
}

data class ChatPrefs(
    val inputMode: InputMode = InputMode.MIXED,
    val themeStyle: ChatThemeStyle = ChatThemeStyle.FOREST,
    val bubbleStyle: BubbleStyle = BubbleStyle.ROUND,
    val backgroundMode: ChatBackgroundMode = ChatBackgroundMode.THEME,
    /** 本地背景图绝对路径（相册或下载） */
    val backgroundImagePath: String? = null,
    val backgroundPresetId: String? = null,
    /** 助手回复完成后自动朗读 */
    val autoSpeakReplies: Boolean = false,
    /** 默认 Edge 小艺，与常见 Hermes `tts.provider: edge` 对齐 */
    val speakEngine: SpeakEngine = SpeakEngine.EDGE,
    /**
     * OpenAI 兼容 TTS 基址（如 `https://api.openai.com/v1` 或 Fish Audio）。
     * 有值时云端/自动朗读走这里，而不是 Agent 聊天地址。
     * Hermes 聊天 API 通常不提供 `/v1/audio/speech`。
     */
    val ttsEndpoint: String = "",
    val ttsApiKey: String = "",
    val ttsModel: String = "",
    val ttsVoice: String = "",
    /** 仅对端侧网关 Agent 生效 */
    val gatewayRouteMode: GatewayRouteMode = GatewayRouteMode.AUTO,
    val shortcuts: List<ShortcutDef> = DEFAULT_SHORTCUTS,
) {
    fun resolvedImagePath(): String? =
        if (backgroundMode == ChatBackgroundMode.IMAGE) {
            backgroundImagePath?.takeIf { it.isNotBlank() }
        } else {
            null
        }

    companion object {
        val DEFAULT_SHORTCUTS = listOf(
            ShortcutDef("today", "今天日程", "今天有什么日程？", ShortcutAction.SEND),
            ShortcutDef("remind", "提醒我…", "提醒我", ShortcutAction.INSERT),
            ShortcutDef("timer", "半小时后", "半小时后提醒我", ShortcutAction.INSERT),
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

    fun setThemeStyle(style: ChatThemeStyle) {
        update { it.copy(themeStyle = style) }
    }

    fun setBubbleStyle(style: BubbleStyle) {
        update { it.copy(bubbleStyle = style) }
    }

    fun setBackgroundThemeOnly() {
        update {
            it.copy(
                backgroundMode = ChatBackgroundMode.THEME,
                backgroundImagePath = null,
                backgroundPresetId = null,
            )
        }
    }

    fun setBackgroundImage(path: String, presetId: String? = null) {
        update {
            it.copy(
                backgroundMode = ChatBackgroundMode.IMAGE,
                backgroundImagePath = path,
                backgroundPresetId = presetId,
            )
        }
    }

    /** 恢复系统默认：森林绿 + 主题渐变背景。 */
    fun resetToSystemDefaultAppearance() {
        update {
            it.copy(
                themeStyle = ChatThemeStyle.FOREST,
                bubbleStyle = BubbleStyle.ROUND,
                backgroundMode = ChatBackgroundMode.THEME,
                backgroundImagePath = null,
                backgroundPresetId = null,
            )
        }
    }

    fun setAutoSpeakReplies(enabled: Boolean) {
        update { it.copy(autoSpeakReplies = enabled) }
    }

    fun setSpeakEngine(engine: SpeakEngine) {
        update { it.copy(speakEngine = engine) }
    }

    fun setTtsEndpoint(value: String) {
        update { it.copy(ttsEndpoint = value.trim()) }
    }

    fun setTtsApiKey(value: String) {
        update { it.copy(ttsApiKey = value.trim()) }
    }

    fun setTtsModel(value: String) {
        update { it.copy(ttsModel = value.trim()) }
    }

    fun setTtsVoice(value: String) {
        update { it.copy(ttsVoice = value.trim()) }
    }

    fun setGatewayRouteMode(mode: GatewayRouteMode) {
        update { it.copy(gatewayRouteMode = mode) }
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
            .putString(KEY_THEME, value.themeStyle.name)
            .putString(KEY_BUBBLE, value.bubbleStyle.name)
            .putString(KEY_BG_MODE, value.backgroundMode.name)
            .putString(KEY_BG_PATH, value.backgroundImagePath)
            .putString(KEY_BG_PRESET, value.backgroundPresetId)
            .putBoolean(KEY_AUTO_SPEAK, value.autoSpeakReplies)
            .putString(KEY_SPEAK_ENGINE, value.speakEngine.name)
            .putString(KEY_TTS_ENDPOINT, value.ttsEndpoint)
            .putString(KEY_TTS_API_KEY, value.ttsApiKey)
            .putString(KEY_TTS_MODEL, value.ttsModel)
            .putString(KEY_TTS_VOICE, value.ttsVoice)
            .putString(KEY_GATEWAY_ROUTE, value.gatewayRouteMode.name)
            .putString(KEY_SHORTCUTS, array.toString())
            .apply()
    }

    private fun load(): ChatPrefs {
        val mode = prefs.getString(KEY_INPUT_MODE, InputMode.MIXED.name)
            ?.let { raw ->
                runCatching { InputMode.valueOf(raw) }.getOrDefault(InputMode.MIXED)
            }
            ?: InputMode.MIXED
        val theme = prefs.getString(KEY_THEME, ChatThemeStyle.FOREST.name)
            ?.let { raw ->
                runCatching { ChatThemeStyle.valueOf(raw) }.getOrDefault(ChatThemeStyle.FOREST)
            }
            ?: ChatThemeStyle.FOREST
        val bubble = prefs.getString(KEY_BUBBLE, BubbleStyle.ROUND.name)
            ?.let { raw ->
                runCatching { BubbleStyle.valueOf(raw) }.getOrDefault(BubbleStyle.ROUND)
            }
            ?: BubbleStyle.ROUND
        val bgMode = prefs.getString(KEY_BG_MODE, ChatBackgroundMode.THEME.name)
            ?.let { raw ->
                runCatching { ChatBackgroundMode.valueOf(raw) }
                    .getOrDefault(ChatBackgroundMode.THEME)
            }
            ?: ChatBackgroundMode.THEME
        val bgPath = prefs.getString(KEY_BG_PATH, null)?.takeIf { it.isNotBlank() }
        val bgPreset = prefs.getString(KEY_BG_PRESET, null)?.takeIf { it.isNotBlank() }
        val autoSpeak = prefs.getBoolean(KEY_AUTO_SPEAK, false)
        val speakEngine = prefs.getString(KEY_SPEAK_ENGINE, SpeakEngine.EDGE.name)
            ?.let { raw ->
                runCatching { SpeakEngine.valueOf(raw) }.getOrDefault(SpeakEngine.EDGE)
            }
            ?: SpeakEngine.EDGE
        val ttsEndpoint = prefs.getString(KEY_TTS_ENDPOINT, "") ?: ""
        val ttsApiKey = prefs.getString(KEY_TTS_API_KEY, "") ?: ""
        val ttsModel = prefs.getString(KEY_TTS_MODEL, "") ?: ""
        val ttsVoice = prefs.getString(KEY_TTS_VOICE, "") ?: ""
        val gatewayRoute = prefs.getString(KEY_GATEWAY_ROUTE, GatewayRouteMode.AUTO.name)
            ?.let { raw ->
                runCatching { GatewayRouteMode.valueOf(raw) }.getOrDefault(GatewayRouteMode.AUTO)
            }
            ?: GatewayRouteMode.AUTO
        val shortcuts = loadShortcuts()
        return ChatPrefs(
            inputMode = mode,
            themeStyle = theme,
            bubbleStyle = bubble,
            backgroundMode = bgMode,
            backgroundImagePath = bgPath,
            backgroundPresetId = bgPreset,
            autoSpeakReplies = autoSpeak,
            speakEngine = speakEngine,
            ttsEndpoint = ttsEndpoint,
            ttsApiKey = ttsApiKey,
            ttsModel = ttsModel,
            ttsVoice = ttsVoice,
            gatewayRouteMode = gatewayRoute,
            shortcuts = shortcuts,
        )
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
        private const val KEY_THEME = "theme_style"
        private const val KEY_BUBBLE = "bubble_style"
        private const val KEY_BG_MODE = "background_mode"
        private const val KEY_BG_PATH = "background_image_path"
        private const val KEY_BG_PRESET = "background_preset_id"
        private const val KEY_AUTO_SPEAK = "auto_speak_replies"
        private const val KEY_SPEAK_ENGINE = "speak_engine"
        private const val KEY_TTS_ENDPOINT = "tts_endpoint"
        private const val KEY_TTS_API_KEY = "tts_api_key"
        private const val KEY_TTS_MODEL = "tts_model"
        private const val KEY_TTS_VOICE = "tts_voice"
        private const val KEY_GATEWAY_ROUTE = "gateway_route_mode"
        private const val KEY_SHORTCUTS = "shortcuts"
    }
}
