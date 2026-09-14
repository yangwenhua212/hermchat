package com.eraherm.hermchat.service

import android.os.Handler
import android.os.Looper
import java.util.Locale
import java.util.concurrent.CopyOnWriteArraySet

/**
 * 语音门控：**TTS 在说话时，麦克风必须闭嘴**。
 *
 * 为什么要有它（老大原话「不能监听它自己读出来的声音啊，要不然它自己回答自己听自己的我服了」）：
 * 监听循环每 300~500ms 就重开一次识别，而 TTS 是把回复念出声的 —— 麦克风把念出去的话
 * 又当成新指令收进去，于是「回答 → 朗读 → 又听到 → 又回答」自己跟自己聊起来。
 * 系统 SpeechRecognizer 不做回声消除（AEC），这种事只能业务层挡，三道闸：
 *   ① 朗读开始 → 立刻停掉当前识别（不等它自然结束，那时已经录进去了）；
 *   ② 朗读期间不开新识别，读完再等一段冷却（喇叭余音 + 识别缓冲残留）；
 *   ③ 刚念过的文字再听到就丢掉（长句子跨识别窗口时兜底）。
 *
 * 进程级单例：朗读可能发生在聊天页，识别跑在 WakeWordService，两边必须看到同一状态。
 * 所有回调都在主线程（SpeechRecognizer / AudioRecord 的宿主线程上都安全）。
 */
object VoiceGate {
    /** 朗读结束后仍不接受识别的冷却时间（喇叭余音 + 缓冲残留）。 */
    private const val COOLDOWN_MS = 800L
    /** 回声比对窗口：这么久以内，「刚读过的文字」再听到就算回声。 */
    private const val ECHO_WINDOW_MS = 10_000L
    /** 短于这个字数的识别结果不当回声（用户真说短指令时别误杀）。 */
    private const val MIN_ECHO_CHARS = 4
    /** 字符二元组重合率阈值：识别结果往往只截到朗读内容的一段，所以看重合度而非全等。 */
    private const val ECHO_RATIO = 0.6

    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<Listener>()

    /** 兜底放行：万一 TTS 引擎哑掉、回调不来，也不许把麦克风永久关死。 */
    private val safety = Runnable {
        if (speaking) {
            markIdle()
        }
    }

    @Volatile
    var speaking: Boolean = false
        private set

    @Volatile
    private var lastSpokeAt: Long = 0L

    @Volatile
    private var lastSpokenText: String = ""

    interface Listener {
        /** 朗读开始：立刻停掉识别（别再收）。 */
        fun onSpeakStart()

        /** 朗读结束（含被 stop 打断）：可以恢复，但要等冷却。 */
        fun onSpeakEnd()
    }

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    /** TtsSpeaker 在每次真正开始朗读时调用。 */
    fun markSpeaking(text: String) {
        lastSpokenText = (text ?: "").trim()
        val wasSpeaking = speaking
        speaking = true
        // 兜底：按文本长度估个上限，超时就放行（TTS 回调有可能永远不来）
        mainHandler.removeCallbacks(safety)
        val budgetMs = (5_000L + lastSpokenText.length * 260L).coerceAtMost(180_000L)
        mainHandler.postDelayed(safety, budgetMs)
        if (!wasSpeaking) {
            post { listeners.forEach { runCatching { it.onSpeakStart() } } }
        }
    }

    /** TtsSpeaker 在队列播完 / 被 stop / 出错收尾时调用。 */
    fun markIdle() {
        mainHandler.removeCallbacks(safety)
        lastSpokeAt = System.currentTimeMillis()
        val wasSpeaking = speaking
        speaking = false
        if (wasSpeaking) {
            post { listeners.forEach { runCatching { it.onSpeakEnd() } } }
        }
    }

    /** 现在能不能开识别：不在朗读、且过了冷却。 */
    fun canListen(): Boolean =
        !speaking && (System.currentTimeMillis() - lastSpokeAt) >= COOLDOWN_MS

    /** 剩下的冷却毫秒数（0 = 现在就能听）。 */
    fun cooldownRemainingMs(): Long =
        (COOLDOWN_MS - (System.currentTimeMillis() - lastSpokeAt)).coerceAtLeast(0L)

    /** 这句识别结果是不是我们刚念出去的回声。 */
    fun isEcho(text: String): Boolean {
        val heard = (text ?: "").trim()
        if (heard.isEmpty() || lastSpokenText.isEmpty()) return false
        // 正在朗读时收到的一切都不是用户说的（canListen 已拦）；这里只比对刚念过的内容
        if (!speaking && System.currentTimeMillis() - lastSpokeAt > ECHO_WINDOW_MS) return false
        return looksLikeEcho(lastSpokenText, heard)
    }

    /** 归一化后互相包含，或二元组重合率够高 → 判为回声。 */
    private fun looksLikeEcho(spoken: String, heard: String): Boolean {
        val a = normalize(spoken)
        val b = normalize(heard)
        if (b.length < MIN_ECHO_CHARS) return false
        if (a.contains(b) || b.contains(a)) return true
        return overlapRatio(a, b) >= ECHO_RATIO
    }

    private fun normalize(raw: String): String =
        raw.lowercase(Locale.ROOT)
            .filter { it.isLetterOrDigit() }

    private fun bigrams(s: String): Set<String> =
        if (s.length < 2) setOf(s) else (0 until s.length - 1).map { s.substring(it, it + 2) }.toSet()

    private fun overlapRatio(a: String, b: String): Double {
        val ga = bigrams(a)
        val gb = bigrams(b)
        if (ga.isEmpty() || gb.isEmpty()) return 0.0
        return ga.intersect(gb).size.toDouble() / minOf(ga.size, gb.size)
    }

    private fun post(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }
}
