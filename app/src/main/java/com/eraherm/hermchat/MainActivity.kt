package com.eraherm.hermchat

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eraherm.hermchat.ui.HermChatAppRoot
import com.eraherm.hermchat.ui.theme.ChatAtmosphere
import com.eraherm.hermchat.ui.theme.HermChatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ingestShare(intent)
        val app = application as HermChatApp
        setContent {
            val chatPrefs by app.chatPrefsStore.prefsFlow.collectAsStateWithLifecycle()
            val darkTheme = ChatAtmosphere.isDark(chatPrefs.themeStyle)
            HermChatTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HermChatAppRoot()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        ingestShare(intent)
    }

    private fun ingestShare(intent: Intent?) {
        (application as? HermChatApp)?.shareInbox?.offerFromIntent(intent)
    }
}
