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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eraherm.hermchat.data.local.BubbleStyle
import com.eraherm.hermchat.data.local.ChatThemeStyle
import com.eraherm.hermchat.data.model.Message
import com.eraherm.hermchat.data.model.MessageRole
import com.eraherm.hermchat.ui.theme.Forest
import com.eraherm.hermchat.ui.theme.Ink
import com.eraherm.hermchat.ui.theme.Line
import com.eraherm.hermchat.ui.theme.SoftGray

@Composable
fun MessageBubble(
    message: Message,
    themeStyle: ChatThemeStyle = ChatThemeStyle.FOREST,
    bubbleStyle: BubbleStyle = BubbleStyle.ROUND,
) {
    var visible by remember(message.id) { mutableStateOf(false) }
    LaunchedEffect(message.id) { visible = true }
    val userColor = when (themeStyle) {
        ChatThemeStyle.FOREST -> Forest
        ChatThemeStyle.INK -> Ink
        ChatThemeStyle.SKY -> Color(0xFF3A7CA5)
    }
    val radius = when (bubbleStyle) {
        BubbleStyle.ROUND -> 18.dp
        BubbleStyle.SOFT -> 22.dp
        BubbleStyle.SQUARE -> 8.dp
    }

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
                                        color = userColor,
                                        shape = bubbleShape(isUser, radius),
                                    )
                                } else {
                                    Modifier
                                        .background(
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                            shape = bubbleShape(isUser, radius),
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = Line.copy(alpha = 0.85f),
                                            shape = bubbleShape(isUser, radius),
                                        )
                                },
                            )
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                    ) {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

private fun bubbleShape(isUser: Boolean, radius: Dp) = RoundedCornerShape(
    topStart = radius,
    topEnd = radius,
    bottomStart = if (isUser) radius else 5.dp,
    bottomEnd = if (isUser) 5.dp else radius,
)
