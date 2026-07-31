package com.eraherm.hermchat.tools

import com.eraherm.hermchat.data.model.ToolCall

/** Prefer alarm for timers / wake-ups; fall back to calendar for meetings. */
object LocalToolPlanner {
    fun plan(userText: String): ToolCall? =
        LocalAlarmPlanner.plan(userText) ?: LocalCalendarPlanner.plan(userText)
}
