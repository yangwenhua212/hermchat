package com.eraherm.hermchat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eraherm.hermchat.HermChatApp
import com.eraherm.hermchat.data.model.AgentProfile
import com.eraherm.hermchat.ui.screens.AboutScreen
import com.eraherm.hermchat.ui.screens.AgentSetupScreen
import com.eraherm.hermchat.ui.screens.ChatPrefsScreen
import com.eraherm.hermchat.ui.screens.ChatScreen
import com.eraherm.hermchat.ui.screens.SetupAssistScreen
import com.eraherm.hermchat.ui.screens.WakeWordSetupScreen

private sealed interface AppDestination {
    data object SetupAssist : AppDestination
    data object Setup : AppDestination
    data object Chat : AppDestination
    data object WakeSetup : AppDestination
    data object ChatPrefs : AppDestination
    data object About : AppDestination
}

@Composable
fun HermChatAppRoot() {
    val app = LocalContext.current.applicationContext as HermChatApp
    val agents by app.agentStore.agents.collectAsStateWithLifecycle()
    val currentId by app.agentStore.currentId.collectAsStateWithLifecycle()

    var destination by remember {
        mutableStateOf<AppDestination>(
            if (app.agentStore.hasAgent()) AppDestination.Chat else AppDestination.SetupAssist,
        )
    }
    var editing by remember { mutableStateOf<AgentProfile?>(null) }
    var setupIsNew by remember { mutableStateOf(!app.agentStore.hasAgent()) }

    when (destination) {
        AppDestination.SetupAssist -> {
            SetupAssistScreen(
                onFinished = {
                    editing = null
                    setupIsNew = false
                    destination = AppDestination.Chat
                },
                onManualSetup = {
                    editing = null
                    setupIsNew = true
                    destination = AppDestination.Setup
                },
                onCancel = if (agents.isNotEmpty()) {
                    {
                        editing = null
                        setupIsNew = false
                        destination = AppDestination.Chat
                    }
                } else {
                    null
                },
            )
        }

        AppDestination.Setup -> {
            AgentSetupScreen(
                editing = if (setupIsNew) null else editing,
                onFinished = {
                    editing = null
                    setupIsNew = false
                    destination = AppDestination.Chat
                },
                onCancel = if (agents.isNotEmpty()) {
                    {
                        editing = null
                        setupIsNew = false
                        destination = AppDestination.Chat
                    }
                } else {
                    {
                        destination = AppDestination.SetupAssist
                    }
                },
            )
        }

        AppDestination.WakeSetup -> {
            WakeWordSetupScreen(
                onBack = { destination = AppDestination.Chat },
            )
        }

        AppDestination.ChatPrefs -> {
            ChatPrefsScreen(
                onBack = { destination = AppDestination.Chat },
                onOpenAbout = { destination = AppDestination.About },
            )
        }

        AppDestination.About -> {
            AboutScreen(
                onBack = { destination = AppDestination.ChatPrefs },
            )
        }

        AppDestination.Chat -> {
            val current = agents.find { it.id == currentId } ?: agents.firstOrNull()
            ChatScreen(
                agent = current,
                agents = agents,
                onSelectAgent = { agent ->
                    app.agentStore.setCurrentId(agent.id)
                },
                onAddAgent = {
                    editing = null
                    setupIsNew = true
                    destination = AppDestination.SetupAssist
                },
                onConfigureAgent = {
                    val target = agents.find { it.id == currentId } ?: current
                    editing = target
                    setupIsNew = false
                    destination = AppDestination.Setup
                },
                onOpenWakeSetup = {
                    destination = AppDestination.WakeSetup
                },
                onOpenChatPrefs = {
                    destination = AppDestination.ChatPrefs
                },
            )
        }
    }
}
