package com.eraherm.hermchat.tools

/**
 * 本机工具风险分级：决定 Agent loop 是否暂停等用户确认。
 * - [READ_ONLY]：可静默执行（读剪贴板等，后续工具）
 * - [WRITE]：改系统状态 / 打开外链等，必须确认卡
 * - [DESTRUCTIVE]：高敏写操作（预留更强文案）；当前与 WRITE 同等须确认
 */
enum class ToolRisk {
    READ_ONLY,
    WRITE,
    DESTRUCTIVE,
    ;

    val requiresUserConfirm: Boolean
        get() = this != READ_ONLY
}
