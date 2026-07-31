package com.eraherm.hermchat.data.local

import android.content.Context
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class WakeEngineKind {
    SYSTEM,
    OFFLINE,
}

data class WakeSettings(
    val enabled: Boolean = false,
    val phrase: String = DEFAULT_PHRASE,
    val autoSend: Boolean = true,
    val engine: WakeEngineKind = WakeEngineKind.SYSTEM,
) {
    companion object {
        const val DEFAULT_PHRASE = "小助手"
        val PRESETS = listOf("小助手", "小黑", "嘿助手", "HxSync")
    }
}

class WakeSettingsStore(
    private val context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<WakeSettings> = _settings.asStateFlow()

    fun update(transform: (WakeSettings) -> WakeSettings) {
        val next = transform(_settings.value)
        prefs.edit()
            .putBoolean(KEY_ENABLED, next.enabled)
            .putString(KEY_PHRASE, next.phrase)
            .putBoolean(KEY_AUTO_SEND, next.autoSend)
            .putString(KEY_ENGINE, next.engine.name)
            .apply()
        _settings.value = next
    }

    fun preferredEngine(): WakeEngineKind {
        val stored = _settings.value.engine
        if (stored == WakeEngineKind.OFFLINE) return WakeEngineKind.OFFLINE
        return if (SpeechRecognizer.isRecognitionAvailable(context)) {
            WakeEngineKind.SYSTEM
        } else {
            WakeEngineKind.OFFLINE
        }
    }

    private fun load(): WakeSettings {
        val engineName = prefs.getString(KEY_ENGINE, null)
        val engine = when {
            engineName != null -> runCatching { WakeEngineKind.valueOf(engineName) }
                .getOrDefault(WakeEngineKind.SYSTEM)
            !SpeechRecognizer.isRecognitionAvailable(context) -> WakeEngineKind.OFFLINE
            else -> WakeEngineKind.SYSTEM
        }
        return WakeSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            phrase = prefs.getString(KEY_PHRASE, WakeSettings.DEFAULT_PHRASE)
                ?: WakeSettings.DEFAULT_PHRASE,
            autoSend = prefs.getBoolean(KEY_AUTO_SEND, true),
            engine = engine,
        )
    }

    companion object {
        private const val PREFS = "hermchat_wake"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_PHRASE = "phrase"
        private const val KEY_AUTO_SEND = "auto_send"
        private const val KEY_ENGINE = "engine"
    }
}
