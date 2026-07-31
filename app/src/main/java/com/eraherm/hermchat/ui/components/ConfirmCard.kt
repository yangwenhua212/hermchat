package com.eraherm.hermchat.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eraherm.hermchat.data.model.ToolCall
import com.eraherm.hermchat.ui.theme.SoftGray

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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "需要你确认后才会操作手机。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SoftGray,
                )
                Text(
                    text = toolCall.summary,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    text = toolCall.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = SoftGray,
                )
            }
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
