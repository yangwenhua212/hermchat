package com.eraherm.hermchat.service

/** Wake / ASR backend. Swap system vs offline without changing VoiceEventBus. */
interface VoiceEngine {
    fun startListeningLoop()
    fun startPushToTalk()
    fun stop()
}
