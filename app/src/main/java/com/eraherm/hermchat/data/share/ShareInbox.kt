package com.eraherm.hermchat.data.share

import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 系统分享入 App 的一次性待办（MainActivity → ChatScreen）。 */
class ShareInbox {
    data class Pending(
        val text: String? = null,
        val uri: Uri? = null,
        val mime: String? = null,
    )

    private val _pending = MutableStateFlow<Pending?>(null)
    val pending: StateFlow<Pending?> = _pending.asStateFlow()

    fun offerFromIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return
        val type = intent.type
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.takeIf { it.isNotBlank() }
        val stream = if (action == Intent.ACTION_SEND) {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.firstOrNull()
        }
        if (text.isNullOrBlank() && stream == null) return
        _pending.value = Pending(text = text, uri = stream, mime = type)
    }

    fun consume(): Pending? {
        val cur = _pending.value ?: return null
        _pending.value = null
        return cur
    }
}
