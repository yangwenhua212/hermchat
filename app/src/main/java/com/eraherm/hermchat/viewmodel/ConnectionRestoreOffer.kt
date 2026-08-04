package com.eraherm.hermchat.viewmodel

/** 自动降级后顶栏「切回」原 ③。 */
data class ConnectionRestoreOffer(
    val agentId: String,
    val agentName: String,
) {
    val actionLabel: String
        get() = "切回 ${agentName.ifBlank { "主力" }}"
}
