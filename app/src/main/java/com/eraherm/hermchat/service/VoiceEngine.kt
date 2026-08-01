package com.eraherm.hermchat.service

/** Wake / ASR backend. Swap system vs offline without changing VoiceEventBus. */
interface VoiceEngine {
    fun startListeningLoop()
    fun startPushToTalk()
    fun stop()

    /**
     * 聊天页在前台时为 true：不再等唤醒词，直接听指令。
     * 退到后台后为 false：恢复「先喊唤醒词」。
     */
    fun setInAppDirectListen(enabled: Boolean) {}
}
