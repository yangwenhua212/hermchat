package com.eraherm.hermchat.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.eraherm.hermchat.ui.theme.Danger
import com.eraherm.hermchat.ui.theme.Live
import com.eraherm.hermchat.ui.theme.SoftGray

@Composable
fun ConnectionStatus(connected: Boolean) {
    val color by animateColorAsState(
        targetValue = if (connected) Live else Danger,
        animationSpec = tween(320),
        label = "connColor",
    )
    val pulse = rememberInfiniteTransition(label = "connPulse")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = if (connected) 1.35f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "connScale",
    )

    Row(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .scale(if (connected) scale else 1f)
                .background(color = color, shape = CircleShape),
        )
        Text(
            text = if (connected) "已连接" else "未连接",
            style = MaterialTheme.typography.labelLarge,
            color = SoftGray,
        )
    }
}
