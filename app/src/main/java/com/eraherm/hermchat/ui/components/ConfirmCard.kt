package com.eraherm.hermchat.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.eraherm.hermchat.data.model.ToolCall

@Composable
fun ConfirmCard(
    toolCall: ToolCall,
    busy: Boolean,
    onAllow: () -> Unit,
    onDeny: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDeny() },
        shape = RoundedCornerShape(22.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = toolCall.title,
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Text(
                text = toolCall.summary,
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        confirmButton = {
            Button(
                onClick = onAllow,
                enabled = !busy,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(if (busy) "执行中…" else "允许")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDeny,
                enabled = !busy,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("取消")
            }
        },
    )
}
