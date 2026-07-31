package com.eraherm.hermchat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.eraherm.hermchat.ui.theme.Forest
import com.eraherm.hermchat.ui.theme.ForestDeep
import com.eraherm.hermchat.ui.theme.MistSoft
import com.eraherm.hermchat.ui.theme.Moss

@Composable
fun AtmosphereBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.3f
    val brush = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                ForestDeep,
                Color(0xFF102E28),
                Color(0xFF0F2823),
            ),
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                MistSoft,
                MaterialTheme.colorScheme.background,
                Color(0xFFF3FAF7),
            ),
            start = Offset.Zero,
            end = Offset(900f, 1400f),
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(brush)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        (if (isDark) Moss else Forest).copy(alpha = if (isDark) 0.16f else 0.08f),
                        Color.Transparent,
                    ),
                    center = Offset(120f, 80f),
                    radius = 520f,
                ),
            ),
        content = content,
    )
}
