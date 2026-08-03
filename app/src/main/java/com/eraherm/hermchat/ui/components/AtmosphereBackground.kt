package com.eraherm.hermchat.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.eraherm.hermchat.data.local.ChatThemeStyle
import com.eraherm.hermchat.ui.theme.ChatAtmosphere
import java.io.File

/** 仅绘制氛围层（主题渐变或壁纸+遮罩），不强制占满父布局以外区域。 */
@Composable
fun AtmosphereBackdrop(
    modifier: Modifier = Modifier,
    themeStyle: ChatThemeStyle = ChatThemeStyle.FOREST,
    imagePath: String? = null,
) {
    val bitmap = remember(imagePath) {
        val path = imagePath?.takeIf { it.isNotBlank() } ?: return@remember null
        val file = File(path)
        if (!file.exists() || file.length() < 2_000L) return@remember null
        runCatching {
            BitmapFactory.Options().run {
                inSampleSize = 2
                BitmapFactory.decodeFile(path, this)?.asImageBitmap()
            }
        }.getOrNull()
    }

    Box(modifier = modifier) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            val scrim = if (ChatAtmosphere.isDark(themeStyle)) {
                Color.Black.copy(alpha = 0.45f)
            } else {
                Color.White.copy(alpha = 0.42f)
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(scrim),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ChatAtmosphere.gradientBrush(themeStyle))
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                ChatAtmosphere.glowColor(themeStyle),
                                Color.Transparent,
                            ),
                            center = Offset(120f, 80f),
                            radius = 520f,
                        ),
                    ),
            )
        }
    }
}

@Composable
fun AtmosphereBackground(
    modifier: Modifier = Modifier,
    themeStyle: ChatThemeStyle = ChatThemeStyle.FOREST,
    imagePath: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        AtmosphereBackdrop(
            modifier = Modifier.fillMaxSize(),
            themeStyle = themeStyle,
            imagePath = imagePath,
        )
        content()
    }
}
