package com.eraherm.hermchat

import android.app.Application
import com.eraherm.hermchat.data.local.AgentStore
import com.eraherm.hermchat.data.local.AppDatabase
import com.eraherm.hermchat.data.local.ChatPrefsStore
import com.eraherm.hermchat.data.local.MessageRepository
import com.eraherm.hermchat.data.local.LocalModelStore
import com.eraherm.hermchat.data.local.WakeSettingsStore
import com.eraherm.hermchat.service.VoiceEventBus
import com.eraherm.hermchat.tools.ToolRegistry

class HermChatApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val messageRepository: MessageRepository by lazy {
        MessageRepository(database.messageDao())
    }
    val agentStore: AgentStore by lazy { AgentStore(this) }
    val wakeSettingsStore: WakeSettingsStore by lazy { WakeSettingsStore(this) }
    val chatPrefsStore: ChatPrefsStore by lazy { ChatPrefsStore(this) }
    val localModelStore: LocalModelStore by lazy { LocalModelStore(this) }
    val voiceEventBus: VoiceEventBus by lazy { VoiceEventBus() }
    val toolRegistry: ToolRegistry by lazy { ToolRegistry(this) }
}
