package com.eraherm.hermchat.ui.components

import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eraherm.hermchat.data.model.Conversation
import com.eraherm.hermchat.ui.theme.SoftGray

@Composable
fun ConversationHistoryMenu(
    conversations: List<Conversation>,
    activeId: String?,
    enabled: Boolean,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(
        onClick = { expanded = true },
        enabled = enabled,
    ) {
        Icon(
            imageVector = Icons.Filled.History,
            contentDescription = "历史对话",
            tint = SoftGray,
        )
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        modifier = Modifier.widthIn(min = 220.dp, max = 300.dp),
    ) {
        if (conversations.isEmpty()) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = "暂无历史",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SoftGray,
                    )
                },
                onClick = { expanded = false },
                enabled = false,
            )
        } else {
            conversations.forEach { conversation ->
                val selected = conversation.id == activeId
                DropdownMenuItem(
                    text = {
                        Text(
                            text = conversation.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {
                        expanded = false
                        if (!selected) onOpen(conversation.id)
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { onDelete(conversation.id) },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "删除",
                                tint = SoftGray,
                            )
                        }
                    },
                )
            }
        }
    }
}
