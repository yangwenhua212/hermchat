package com.eraherm.hermchat.tools

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import com.eraherm.hermchat.data.model.ToolCall
import com.eraherm.hermchat.data.model.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.TimeZone

class CalendarTool(
    private val context: Context,
) : PhoneTool {
    override val name: String = NAME

    override val requiredPermissions: Array<String> = arrayOf(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR,
    )

    override suspend fun execute(call: ToolCall): ToolResult = withContext(Dispatchers.IO) {
        try {
            val title = call.arguments["title"]?.ifBlank { null } ?: "HxSync 日程"
            val begin = call.arguments["beginMs"]?.toLongOrNull()
                ?: return@withContext ToolResult(
                    toolCallId = call.id,
                    name = name,
                    success = false,
                    message = "缺少开始时间 beginMs",
                )
            val end = call.arguments["endMs"]?.toLongOrNull() ?: (begin + 60 * 60 * 1000L)
            val description = call.arguments["description"].orEmpty()

            val calendarId = resolveCalendarId()
                ?: return@withContext ToolResult(
                    toolCallId = call.id,
                    name = name,
                    success = false,
                    message = "找不到可写入的系统日历",
                )

            val values = ContentValues().apply {
                put(CalendarContract.Events.DTSTART, begin)
                put(CalendarContract.Events.DTEND, end)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DESCRIPTION, description)
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            }
            context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                ?: return@withContext ToolResult(
                    toolCallId = call.id,
                    name = name,
                    success = false,
                    message = "写入日历失败",
                )
            ToolResult(
                toolCallId = call.id,
                name = name,
                success = true,
                message = "已创建日程「$title」",
            )
        } catch (e: SecurityException) {
            ToolResult(
                toolCallId = call.id,
                name = name,
                success = false,
                message = "没有日历权限",
            )
        } catch (e: Exception) {
            ToolResult(
                toolCallId = call.id,
                name = name,
                success = false,
                message = e.message ?: "日历操作失败",
            )
        }
    }

    private fun resolveCalendarId(): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.VISIBLE,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
        )
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            null,
            null,
            null,
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndex(CalendarContract.Calendars._ID)
            val visibleIdx = cursor.getColumnIndex(CalendarContract.Calendars.VISIBLE)
            val accessIdx = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)
            while (cursor.moveToNext()) {
                val visible = visibleIdx < 0 || cursor.getInt(visibleIdx) == 1
                val access = if (accessIdx >= 0) cursor.getInt(accessIdx) else 0
                val canWrite = access >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR
                if (visible && canWrite && idIdx >= 0) {
                    return cursor.getLong(idIdx)
                }
            }
        }
        return null
    }

    companion object {
        const val NAME = "calendar.create"
    }
}
