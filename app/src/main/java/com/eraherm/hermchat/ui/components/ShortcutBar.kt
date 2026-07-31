package com.eraherm.hermchat.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eraherm.hermchat.data.local.ShortcutDef
import com.eraherm.hermchat.ui.theme.Line
import com.eraherm.hermchat.ui.theme.SoftGray

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShortcutBar(
    shortcuts: List<ShortcutDef>,
    enabled: Boolean,
    onClick: (ShortcutDef) -> Unit,
    onMoveLeft: (ShortcutDef) -> Unit,
    onMoveRight: (ShortcutDef) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (shortcuts.isEmpty()) return

    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(shortcuts, key = { _, item -> item.id }) { index, shortcut ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                border = BorderStroke(1.dp, Line.copy(alpha = 0.95f)),
                modifier = Modifier.combinedClickable(
                    enabled = enabled,
                    onClick = { onClick(shortcut) },
                    onLongClick = {
                        if (index > 0) onMoveLeft(shortcut) else onMoveRight(shortcut)
                    },
                ),
            ) {
                Text(
                    text = shortcut.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onBackground
                    } else {
                        SoftGray
                    },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}
