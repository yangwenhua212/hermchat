package com.eraherm.hermchat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Forest,
    onPrimary = Color.White,
    primaryContainer = MistSoft,
    onPrimaryContainer = ForestDeep,
    secondary = ForestMid,
    onSecondary = Color.White,
    background = Mist,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = MistSoft,
    onSurfaceVariant = SoftGray,
    outline = Line,
    error = Danger,
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Moss,
    onPrimary = ForestDeep,
    primaryContainer = ForestMid,
    onPrimaryContainer = Mist,
    secondary = MistSoft,
    onSecondary = ForestDeep,
    background = ForestDeep,
    onBackground = Mist,
    surface = Color(0xFF143029),
    onSurface = Mist,
    surfaceVariant = Color(0xFF1A3A32),
    onSurfaceVariant = SoftGray,
    outline = Color(0xFF2F4F45),
    error = Danger,
    onError = Color.White,
)

@Composable
fun HermChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}
