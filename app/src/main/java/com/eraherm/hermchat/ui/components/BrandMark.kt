package com.eraherm.hermchat.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.sp
import com.eraherm.hermchat.ui.theme.BrandFont
import com.eraherm.hermchat.ui.theme.SoftGray

@Composable
fun BrandMark(
    subtitle: String? = null,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(520, easing = FastOutSlowInEasing),
        label = "brandAlpha",
    )
    val offset by animateFloatAsState(
        targetValue = if (visible) 0f else 12f,
        animationSpec = tween(520, easing = FastOutSlowInEasing),
        label = "brandOffset",
    )

    Column(
        modifier = modifier
            .alpha(alpha)
            .graphicsLayer { translationY = offset },
    ) {
        Text(
            text = "HxSync",
            style = if (compact) {
                MaterialTheme.typography.headlineSmall
            } else {
                MaterialTheme.typography.displaySmall
            },
            fontFamily = BrandFont,
            color = MaterialTheme.colorScheme.onBackground,
            letterSpacing = (-0.8).sp,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = SoftGray,
            )
        }
    }
}
