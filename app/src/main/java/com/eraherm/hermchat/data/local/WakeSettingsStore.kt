package com.eraherm.hermchat.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WakeSettings(
    val enabled: Boolean = false,
    val phrase: String = DEFAULT_PHRASE,
    val autoSend: Boolean = true,
) {
    companion object {
        const val DEFAULT_PHRASE = "小助手"
        val PRESETS = listOf("小助手", "小黑", "小龙虾", "HxSync")
    }
}

class WakeSettingsStore(
    context: Context,
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
            .apply()
        _settings.value = next
    }

    private fun load(): WakeSettings = WakeSettings(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        phrase = prefs.getString(KEY_PHRASE, WakeSettings.DEFAULT_PHRASE)
            ?: WakeSettings.DEFAULT_PHRASE,
        autoSend = prefs.getBoolean(KEY_AUTO_SEND, true),
    )

    companion object {
        private const val PREFS = "hermchat_wake"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_PHRASE = "phrase"
        private const val KEY_AUTO_SEND = "auto_send"
    }
}
