package com.eraherm.hermchat.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eraherm.hermchat.data.model.Message
import com.eraherm.hermchat.data.model.MessageRole
import com.eraherm.hermchat.ui.theme.Line
import com.eraherm.hermchat.ui.theme.SoftGray

@Composable
fun MessageBubble(message: Message) {
    var visible by remember(message.id) { mutableStateOf(false) }
    LaunchedEffect(message.id) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 6 },
    ) {
        when (message.role) {
            MessageRole.SYSTEM -> {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SoftGray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            MessageRole.USER, MessageRole.ASSISTANT -> {
                val isUser = message.role == MessageRole.USER
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 320.dp)
                            .then(
                                if (isUser) {
                                    Modifier.background(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = bubbleShape(isUser),
                                    )
                                } else {
                                    Modifier
                                        .background(
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                            shape = bubbleShape(isUser),
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = Line.copy(alpha = 0.85f),
                                            shape = bubbleShape(isUser),
                                        )
                                },
                            )
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                    ) {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isUser) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun bubbleShape(isUser: Boolean) = RoundedCornerShape(
    topStart = 18.dp,
    topEnd = 18.dp,
    bottomStart = if (isUser) 18.dp else 5.dp,
    bottomEnd = if (isUser) 5.dp else 18.dp,
)
