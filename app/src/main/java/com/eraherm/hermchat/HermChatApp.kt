package com.eraherm.hermchat

import android.app.Application
import com.eraherm.hermchat.data.local.AgentStore
import com.eraherm.hermchat.data.local.AppDatabase
import com.eraherm.hermchat.data.local.ChatPrefsStore
import com.eraherm.hermchat.data.local.ConversationRepository
import com.eraherm.hermchat.data.local.MessageRepository
import com.eraherm.hermchat.data.local.LocalModelStore
import com.eraherm.hermchat.data.local.WakeSettingsStore
import com.eraherm.hermchat.data.local.WallpaperStore
import com.eraherm.hermchat.data.memory.LocalMemoryStore
import com.eraherm.hermchat.data.share.ShareInbox
import com.eraherm.hermchat.data.network.AgentSessionHolder
import com.eraherm.hermchat.service.ReplySpeaker
import com.eraherm.hermchat.service.TtsSpeaker
import com.eraherm.hermchat.service.VoiceCloudBridge
import com.eraherm.hermchat.service.VoiceEventBus
import com.eraherm.hermchat.tools.ToolRegistry

class HermChatApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val conversationRepository: ConversationRepository by lazy {
        ConversationRepository(this, database.conversationDao())
    }
    val messageRepository: MessageRepository by lazy {
        MessageRepository(database.messageDao(), conversationRepository)
    }
    val agentStore: AgentStore by lazy { AgentStore(this) }
    val wakeSettingsStore: WakeSettingsStore by lazy { WakeSettingsStore(this) }
    val chatPrefsStore: ChatPrefsStore by lazy { ChatPrefsStore(this) }
    val memoryStore: LocalMemoryStore by lazy {
        LocalMemoryStore(database.localMemoryDao(), chatPrefsStore)
    }
    val shareInbox: ShareInbox by lazy { ShareInbox() }
    val wallpaperStore: WallpaperStore by lazy { WallpaperStore(this) }
    val localModelStore: LocalModelStore by lazy { LocalModelStore(this) }
    val agentSessionHolder: AgentSessionHolder by lazy { AgentSessionHolder() }
    val voiceEventBus: VoiceEventBus by lazy { VoiceEventBus() }
    val toolRegistry: ToolRegistry by lazy { ToolRegistry(this) }
    val voiceCloudBridge: VoiceCloudBridge by lazy { VoiceCloudBridge(this) }
    val ttsSpeaker: TtsSpeaker by lazy { TtsSpeaker(this) }
    val replySpeaker: ReplySpeaker by lazy {
        ReplySpeaker(
            context = this,
            local = ttsSpeaker,
            agentStore = agentStore,
            chatPrefsStore = chatPrefsStore,
        )
    }

    override fun onCreate() {
        super.onCreate()
        voiceCloudBridge.start()
        ttsSpeaker.ensureStarted()
    }
}
