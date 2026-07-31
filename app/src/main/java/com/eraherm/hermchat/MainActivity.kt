package com.eraherm.hermchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.eraherm.hermchat.ui.HermChatAppRoot
import com.eraherm.hermchat.ui.theme.HermChatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HermChatTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HermChatAppRoot()
                }
            }
        }
    }
}
