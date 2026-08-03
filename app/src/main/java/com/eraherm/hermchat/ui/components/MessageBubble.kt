package com.eraherm.hermchat.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eraherm.hermchat.data.local.BubbleStyle
import com.eraherm.hermchat.data.local.ChatThemeStyle
import com.eraherm.hermchat.data.model.Message
import com.eraherm.hermchat.data.model.MessageRole
import com.eraherm.hermchat.ui.theme.ChatAtmosphere
import com.eraherm.hermchat.ui.theme.Line
import com.eraherm.hermchat.ui.theme.SoftGray

@Composable
fun MessageBubble(
    message: Message,
    themeStyle: ChatThemeStyle = ChatThemeStyle.FOREST,
    bubbleStyle: BubbleStyle = BubbleStyle.ROUND,
    isStreaming: Boolean = false,
    isSpeaking: Boolean = false,
    onSpeakClick: (() -> Unit)? = null,
) {
    val userColor = ChatAtmosphere.accent(themeStyle)
    val radius = when (bubbleStyle) {
        BubbleStyle.ROUND -> 18.dp
        BubbleStyle.SOFT -> 22.dp
        BubbleStyle.SQUARE -> 8.dp
    }

    when (message.role) {
        MessageRole.SYSTEM -> {
            SelectionContainer {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SoftGray,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 2.dp),
                )
            }
        }

        MessageRole.USER, MessageRole.ASSISTANT -> {
            val isUser = message.role == MessageRole.USER
            val maxBubbleWidth = (LocalConfiguration.current.screenWidthDp * 0.78f).dp
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = maxBubbleWidth)
                            .then(
                                if (isUser) {
                                    Modifier.background(
                                        color = userColor,
                                        shape = bubbleShape(isUser, radius),
                                    )
                                } else {
                                    Modifier
                                        .background(
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                                            shape = bubbleShape(isUser, radius),
                                        )
                                        .border(
                                            width = 0.5.dp,
                                            color = Line.copy(alpha = 0.55f),
                                            shape = bubbleShape(isUser, radius),
                                        )
                                },
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        SelectionContainer {
                            Text(
                                text = if (isStreaming && message.content.isNotEmpty()) {
                                    message.content + "▍"
                                } else {
                                    message.content
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                if (!isUser && !isStreaming && onSpeakClick != null && message.content.isNotBlank()) {
                    IconButton(
                        onClick = onSpeakClick,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = if (isSpeaking) Icons.Filled.Stop else Icons.Filled.VolumeUp,
                            contentDescription = if (isSpeaking) "停止朗读" else "朗读",
                            tint = if (isSpeaking) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                SoftGray
                            },
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TypingBubble(
    bubbleStyle: BubbleStyle = BubbleStyle.ROUND,
) {
    val radius = when (bubbleStyle) {
        BubbleStyle.ROUND -> 18.dp
        BubbleStyle.SOFT -> 22.dp
        BubbleStyle.SQUARE -> 8.dp
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    shape = bubbleShape(isUser = false, radius = radius),
                )
                .border(
                    width = 0.5.dp,
                    color = Line.copy(alpha = 0.55f),
                    shape = bubbleShape(isUser = false, radius = radius),
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            TypingDots()
        }
    }
}

@Composable
private fun TypingDots() {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 420, delayMillis = index * 120, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$index",
            )
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .alpha(alpha)
                    .background(SoftGray, CircleShape),
            )
        }
    }
}

private fun bubbleShape(isUser: Boolean, radius: Dp) = RoundedCornerShape(
    topStart = radius,
    topEnd = radius,
    bottomStart = if (isUser) radius else 5.dp,
    bottomEnd = if (isUser) 5.dp else radius,
)
