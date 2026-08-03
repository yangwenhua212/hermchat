package com.eraherm.hermchat.data.local

import android.app.ActivityManager
import android.content.Context

/** Runtime checks before loading on-device LLM weights. */
object DeviceCapability {
    /** Floor for ~0.5B-class INT8 on-device inference. */
    const val MIN_TOTAL_RAM_MB = 3000L
    const val MIN_AVAIL_RAM_MB = 400L

    fun memorySnapshot(context: Context): MemorySnapshot {
        val am = context.getSystemService(ActivityManager::class.java)
            ?: return MemorySnapshot(0, 0, lowMemory = true)
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return MemorySnapshot(
            totalMb = info.totalMem / (1024L * 1024L),
            availMb = info.availMem / (1024L * 1024L),
            lowMemory = info.lowMemory,
        )
    }

    fun canRunLocalLlm(context: Context): Boolean {
        val snap = memorySnapshot(context)
        return !snap.lowMemory &&
            snap.totalMb >= MIN_TOTAL_RAM_MB &&
            snap.availMb >= MIN_AVAIL_RAM_MB
    }

    data class MemorySnapshot(
        val totalMb: Long,
        val availMb: Long,
        val lowMemory: Boolean,
    )
}
