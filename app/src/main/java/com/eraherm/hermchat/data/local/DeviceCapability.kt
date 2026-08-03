package com.eraherm.hermchat.data.local

import android.app.ActivityManager
import android.content.Context

/** Runtime checks before loading on-device LLM weights. */
object DeviceCapability {
    /** Floor for ~0.5B-class INT8 on-device inference. */
    const val MIN_TOTAL_RAM_MB = 3000L
    const val MIN_AVAIL_RAM_MB = 600L

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

    /**
     * 按模型体积估计门槛。MediaPipe 原生加载失败会直接杀进程，
     * [runCatching] 拦不住，必须在加载前拒掉。
     */
    fun requirementsForModelBytes(approxBytes: Long): MemoryRequirements {
        val modelMb = (approxBytes / (1024L * 1024L)).coerceAtLeast(100L)
        return when {
            // TinyLlama 1.1B / 更大权重：进聊天就预加载极易 OOM 闪退
            modelMb >= 900L -> MemoryRequirements(
                minTotalMb = 5500L,
                minAvailMb = 1600L,
                label = "约 ${modelMb}MB 级模型",
            )
            // Qwen 0.5B / Gemma 1B 量级
            modelMb >= 400L -> MemoryRequirements(
                minTotalMb = 3500L,
                minAvailMb = 900L,
                label = "约 ${modelMb}MB 级模型",
            )
            else -> MemoryRequirements(
                minTotalMb = MIN_TOTAL_RAM_MB,
                minAvailMb = MIN_AVAIL_RAM_MB,
                label = "轻量模型",
            )
        }
    }

    fun canRunLocalLlm(
        context: Context,
        approxModelBytes: Long = 500L * 1024L * 1024L,
    ): Boolean {
        val snap = memorySnapshot(context)
        val req = requirementsForModelBytes(approxModelBytes)
        return !snap.lowMemory &&
            snap.totalMb >= req.minTotalMb &&
            snap.availMb >= req.minAvailMb
    }

    fun refuseReason(
        context: Context,
        approxModelBytes: Long = 500L * 1024L * 1024L,
    ): String? {
        val snap = memorySnapshot(context)
        val req = requirementsForModelBytes(approxModelBytes)
        return when {
            snap.lowMemory -> "系统处于低内存状态，暂不加载本地模型。"
            snap.totalMb < req.minTotalMb ->
                "该机总内存约 ${snap.totalMb}MB，加载${req.label}建议 ≥${req.minTotalMb}MB。可改用更小的 Qwen2.5 0.5B，或先关后台再试。"
            snap.availMb < req.minAvailMb ->
                "当前可用内存约 ${snap.availMb}MB，加载${req.label}建议可用 ≥${req.minAvailMb}MB。请先清理后台后再试，或换更小模型。"
            else -> null
        }
    }

    data class MemorySnapshot(
        val totalMb: Long,
        val availMb: Long,
        val lowMemory: Boolean,
    )

    data class MemoryRequirements(
        val minTotalMb: Long,
        val minAvailMb: Long,
        val label: String,
    )
}
