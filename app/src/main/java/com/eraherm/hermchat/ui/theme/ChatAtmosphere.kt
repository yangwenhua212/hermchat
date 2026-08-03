package com.eraherm.hermchat.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.eraherm.hermchat.data.local.ChatThemeStyle

/** 主题色 → 气泡强调色 + 聊天页渐变（不仅改框）。 */
object ChatAtmosphere {
    fun accent(style: ChatThemeStyle): Color = when (style) {
        ChatThemeStyle.FOREST -> Forest
        ChatThemeStyle.MIST -> Moss
        ChatThemeStyle.SKY -> Color(0xFF3A7CA5)
        ChatThemeStyle.AQUA -> Color(0xFF2A9D8F)
        ChatThemeStyle.CLOUD -> Color(0xFF6B7C85)
        ChatThemeStyle.APRICOT -> Color(0xFFC47B4A)
        ChatThemeStyle.INK -> Ink
    }

    fun gradientBrush(style: ChatThemeStyle): Brush = when (style) {
        ChatThemeStyle.FOREST -> Brush.linearGradient(
            colors = listOf(MistSoft, Paper, Color(0xFFF3FAF7)),
            start = Offset.Zero,
            end = Offset(900f, 1400f),
        )
        ChatThemeStyle.MIST -> Brush.verticalGradient(
            colors = listOf(Color(0xFFF2F8F5), Color(0xFFE8F2ED), Color(0xFFDCEBE3)),
        )
        ChatThemeStyle.SKY -> Brush.verticalGradient(
            colors = listOf(Color(0xFFF0F7FC), Color(0xFFE3F0F9), Color(0xFFD2E6F4)),
        )
        ChatThemeStyle.AQUA -> Brush.verticalGradient(
            colors = listOf(Color(0xFFEFF9F7), Color(0xFFDFF3EF), Color(0xFFC8E9E3)),
        )
        ChatThemeStyle.CLOUD -> Brush.verticalGradient(
            colors = listOf(Color(0xFFF7F8F9), Color(0xFFEEF1F3), Color(0xFFE4E8EC)),
        )
        ChatThemeStyle.APRICOT -> Brush.verticalGradient(
            colors = listOf(Color(0xFFFFF6F0), Color(0xFFFCE9DC), Color(0xFFF5DCC8)),
        )
        ChatThemeStyle.INK -> Brush.verticalGradient(
            colors = listOf(ForestDeep, Color(0xFF102E28), Color(0xFF0F2823)),
        )
    }

    fun glowColor(style: ChatThemeStyle): Color = when (style) {
        ChatThemeStyle.INK -> Moss.copy(alpha = 0.16f)
        ChatThemeStyle.SKY -> Color(0xFF3A7CA5).copy(alpha = 0.10f)
        ChatThemeStyle.AQUA -> Color(0xFF2A9D8F).copy(alpha = 0.10f)
        ChatThemeStyle.CLOUD -> Color(0xFF6B7C85).copy(alpha = 0.08f)
        ChatThemeStyle.APRICOT -> Color(0xFFC47B4A).copy(alpha = 0.08f)
        ChatThemeStyle.MIST -> Moss.copy(alpha = 0.08f)
        ChatThemeStyle.FOREST -> Forest.copy(alpha = 0.08f)
    }

    fun isDark(style: ChatThemeStyle): Boolean = style == ChatThemeStyle.INK
}
