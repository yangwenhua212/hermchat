package com.eraherm.hermchat.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eraherm.hermchat.HermChatApp
import com.eraherm.hermchat.data.local.BubbleStyle
import com.eraherm.hermchat.data.local.ChatPrefs
import com.eraherm.hermchat.data.local.ChatPrefsStore
import com.eraherm.hermchat.data.local.ChatThemeStyle
import com.eraherm.hermchat.data.local.InputMode
import com.eraherm.hermchat.ui.components.AtmosphereBackground
import com.eraherm.hermchat.ui.components.BrandMark
import com.eraherm.hermchat.ui.theme.Line
import com.eraherm.hermchat.ui.theme.SoftGray

/** 设置树：根目录是文件夹，点进去才看详情。 */
private sealed interface PrefsFolder {
    data object Root : PrefsFolder
    data object Input : PrefsFolder
    data object Appearance : PrefsFolder
    data object Shortcuts : PrefsFolder
}

@Composable
fun ChatPrefsScreen(
    onBack: () -> Unit,
    onOpenAbout: () -> Unit = {},
) {
    val app = LocalContext.current.applicationContext as HermChatApp
    val chatPrefs by app.chatPrefsStore.prefsFlow.collectAsStateWithLifecycle()
    var folder by remember { mutableStateOf<PrefsFolder>(PrefsFolder.Root) }

    AtmosphereBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(20.dp),
        ) {
            BrandMark(compact = true)
            Spacer(modifier = Modifier.height(16.dp))

            AnimatedContent(
                targetState = folder,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "prefsFolder",
                modifier = Modifier.weight(1f),
            ) { current ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    when (current) {
                        PrefsFolder.Root -> PrefsRoot(
                            prefs = chatPrefs,
                            store = app.chatPrefsStore,
                            onOpenInput = { folder = PrefsFolder.Input },
                            onOpenAppearance = { folder = PrefsFolder.Appearance },
                            onOpenShortcuts = { folder = PrefsFolder.Shortcuts },
                            onOpenAbout = onOpenAbout,
                        )
                        PrefsFolder.Input -> PrefsInputDetail(
                            prefs = chatPrefs,
                            store = app.chatPrefsStore,
                        )
                        PrefsFolder.Appearance -> PrefsAppearanceDetail(
                            prefs = chatPrefs,
                            store = app.chatPrefsStore,
                        )
                        PrefsFolder.Shortcuts -> PrefsShortcutsDetail(
                            prefs = chatPrefs,
                            store = app.chatPrefsStore,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    onClick = {
                        if (folder == PrefsFolder.Root) onBack() else folder = PrefsFolder.Root
                    },
                ) {
                    Text(if (folder == PrefsFolder.Root) "返回" else "上级")
                }
                if (folder == PrefsFolder.Root) {
                    TextButton(onClick = onOpenAbout) { Text("关于") }
                }
            }
        }
    }
}

@Composable
private fun PrefsRoot(
    prefs: ChatPrefs,
    store: ChatPrefsStore,
    onOpenInput: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenShortcuts: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    PrefsLeafRow(
        title = "朗读回复",
        trailing = {
            Switch(
                checked = prefs.autoSpeakReplies,
                onCheckedChange = { store.setAutoSpeakReplies(it) },
            )
        },
    )
    PrefsFolderRow(
        title = "默认输入",
        summary = prefs.inputMode.label,
        onClick = onOpenInput,
    )
    PrefsFolderRow(
        title = "外观",
        summary = "${prefs.themeStyle.label} · ${prefs.bubbleStyle.label}",
        onClick = onOpenAppearance,
    )
    PrefsFolderRow(
        title = "快捷指令",
        summary = "${prefs.shortcuts.size} 条",
        onClick = onOpenShortcuts,
    )
    PrefsFolderRow(
        title = "关于",
        summary = null,
        onClick = onOpenAbout,
    )
}

@Composable
private fun PrefsInputDetail(
    prefs: ChatPrefs,
    store: ChatPrefsStore,
) {
    Text("默认输入", style = MaterialTheme.typography.titleMedium)
    InputMode.entries.forEach { mode ->
        PrefsOptionRow(
            title = mode.label,
            selected = prefs.inputMode == mode,
            onClick = { store.setInputMode(mode) },
        )
    }
}

@Composable
private fun PrefsAppearanceDetail(
    prefs: ChatPrefs,
    store: ChatPrefsStore,
) {
    Text("聊天主题色", style = MaterialTheme.typography.titleMedium)
    ChatThemeStyle.entries.forEach { style ->
        PrefsOptionRow(
            title = style.label,
            selected = prefs.themeStyle == style,
            onClick = { store.setThemeStyle(style) },
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    Text("气泡样式", style = MaterialTheme.typography.titleMedium)
    BubbleStyle.entries.forEach { style ->
        PrefsOptionRow(
            title = style.label,
            selected = prefs.bubbleStyle == style,
            onClick = { store.setBubbleStyle(style) },
        )
    }
}

@Composable
private fun PrefsShortcutsDetail(
    prefs: ChatPrefs,
    store: ChatPrefsStore,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("快捷指令", style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = { store.resetShortcuts() }) {
            Text("恢复默认")
        }
    }
    prefs.shortcuts.forEachIndexed { index, shortcut ->
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, Line),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = shortcut.label,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                )
                IconButton(
                    onClick = { store.moveShortcut(shortcut.id, -1) },
                    enabled = index > 0,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "前移",
                        tint = if (index > 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            SoftGray
                        },
                    )
                }
                IconButton(
                    onClick = { store.moveShortcut(shortcut.id, 1) },
                    enabled = index < prefs.shortcuts.lastIndex,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "后移",
                        tint = if (index < prefs.shortcuts.lastIndex) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            SoftGray
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PrefsFolderRow(
    title: String,
    summary: String?,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, Line),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (!summary.isNullOrBlank()) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SoftGray,
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = SoftGray,
            )
        }
    }
}

@Composable
private fun PrefsLeafRow(
    title: String,
    trailing: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, Line),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            trailing()
        }
    }
}

@Composable
private fun PrefsOptionRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            ),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else Line,
        ),
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
