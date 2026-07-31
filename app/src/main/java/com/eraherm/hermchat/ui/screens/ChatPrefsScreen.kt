package com.eraherm.hermchat.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eraherm.hermchat.HermChatApp
import com.eraherm.hermchat.data.local.InputMode
import com.eraherm.hermchat.data.local.ShortcutAction
import com.eraherm.hermchat.ui.components.AtmosphereBackground
import com.eraherm.hermchat.ui.components.BrandMark
import com.eraherm.hermchat.ui.theme.Line
import com.eraherm.hermchat.ui.theme.SoftGray

@Composable
fun ChatPrefsScreen(
    onBack: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as HermChatApp
    val chatPrefs by app.chatPrefsStore.prefsFlow.collectAsStateWithLifecycle()

    AtmosphereBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(20.dp),
        ) {
            BrandMark(
                subtitle = "快捷指令与输入方式 · 界面听你的",
                compact = true,
            )
            Text(
                text = "点指令可插入或发送自然语言；排序会立刻反映在聊天页。",
                style = MaterialTheme.typography.bodyMedium,
                color = SoftGray,
                modifier = Modifier.padding(top = 10.dp, bottom = 16.dp),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("默认输入", style = MaterialTheme.typography.titleMedium)
                InputMode.entries.forEach { mode ->
                    val selected = chatPrefs.inputMode == mode
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selected,
                                onClick = { app.chatPrefsStore.setInputMode(mode) },
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
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(mode.label, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = mode.hint,
                                style = MaterialTheme.typography.bodyMedium,
                                color = SoftGray,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("快捷指令顺序", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { app.chatPrefsStore.resetShortcuts() }) {
                        Text("恢复默认")
                    }
                }

                chatPrefs.shortcuts.forEachIndexed { index, shortcut ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, Line),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                                Text(shortcut.label, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = when (shortcut.action) {
                                        ShortcutAction.INSERT -> "插入 · ${shortcut.text}"
                                        ShortcutAction.SEND -> "发送 · ${shortcut.text}"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SoftGray,
                                )
                            }
                            IconButton(
                                onClick = { app.chatPrefsStore.moveShortcut(shortcut.id, -1) },
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
                                onClick = { app.chatPrefsStore.moveShortcut(shortcut.id, 1) },
                                enabled = index < chatPrefs.shortcuts.lastIndex,
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "后移",
                                    tint = if (index < chatPrefs.shortcuts.lastIndex) {
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

            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onBack) { Text("返回") }
        }
    }
}
