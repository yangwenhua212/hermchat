package com.eraherm.hermchat.data.network

import com.eraherm.hermchat.data.local.AttachmentKind
import com.eraherm.hermchat.data.model.AgentProfile

/** 二期附件：文本各通道可发；图片/PDF 首页需 vision 通道。 */
object AttachmentSupport {
    fun canSend(agent: AgentProfile?, kind: AttachmentKind): Boolean = when (kind) {
        AttachmentKind.TEXT -> agent != null
        AttachmentKind.IMAGE, AttachmentKind.PDF -> VisionSupport.canSendImage(agent)
    }

    fun unsupportedStatus(agent: AgentProfile?, kind: AttachmentKind): String = when (kind) {
        AttachmentKind.TEXT -> "请先配置 Agent"
        AttachmentKind.IMAGE, AttachmentKind.PDF -> VisionSupport.unsupportedStatus(agent)
    }
}
