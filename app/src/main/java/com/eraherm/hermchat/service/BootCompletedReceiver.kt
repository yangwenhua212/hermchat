package com.eraherm.hermchat.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.eraherm.hermchat.HermChatApp

/**
 * 开机后若用户开启了「开机恢复监听」且上次为启用状态，则拉起唤醒前台服务。
 * 部分机型仍需自启/电池白名单，否则会被系统拦下。
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val app = context.applicationContext as? HermChatApp ?: return
        val settings = app.wakeSettingsStore.settings.value
        if (!settings.bootAutoStart || !settings.enabled) return
        WakeWordService.start(context)
    }
}
