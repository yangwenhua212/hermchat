package com.eraherm.hermchat.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface VoiceEvent {
    data class WakeDetected(val phrase: String) : VoiceEvent
    data class Transcript(
        val text: String,
        val autoSend: Boolean,
    ) : VoiceEvent

    data class Status(val message: String) : VoiceEvent
    data class Error(val message: String) : VoiceEvent
}

class VoiceEventBus {
    private val _events = MutableSharedFlow<VoiceEvent>(
        extraBufferCapacity = 16,
    )
    val events: SharedFlow<VoiceEvent> = _events.asSharedFlow()

    fun emit(event: VoiceEvent) {
        _events.tryEmit(event)
    }
}
