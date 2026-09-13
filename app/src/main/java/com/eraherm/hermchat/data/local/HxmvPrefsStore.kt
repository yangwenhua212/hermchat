package com.eraherm.hermchat.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** HxMV 的三种生成后端（label 即界面文案，不另挂说明句）。 */
enum class HxmvProvider(val wire: String, val label: String) {
    LOCAL("local", "本地渲染"),
    ZHIPU("zhipu", "智谱真视频"),
    MOCK("mock", "模拟世界"),
}

/**
 * HxMV 内容生产接入配置。
 *
 * [baseUrl] 同时支持远端实例与手机本机（Termux 起在 127.0.0.1:8668）——
 * 两种模式走同一套接口，只有地址不同。
 */
data class HxmvConfig(
    val baseUrl: String = DEFAULT_BASE,
    val token: String = "",
    val provider: HxmvProvider = HxmvProvider.LOCAL,
    val project: String = "",
) {
    companion object {
        /**
         * **不预填任何实例地址**：HxSync 是开源 App，如果默认指向作者自己的实例，
         * 那么每个下载安装的人都会把请求打到作者服务器上（老大原话：
         * 「不要自动配地址我框架的地址啊，要不然别人下载不就用到我的东西了吗」）。
         * 每个用户填自己的：本机 Termux 见 [LOCAL_BASE]，或自己部署的域名。
         */
        const val DEFAULT_BASE = ""
        /** 手机本机跑的实例（Termux）——这是用户自己的设备，可以直接给建议值。 */
        const val LOCAL_BASE = "http://127.0.0.1:8668"
    }
}

class HxmvPrefsStore(context: Context) {
    private val prefs = SecurePrefs.open(context, PREFS)

    private val _config = MutableStateFlow(load())
    val config: StateFlow<HxmvConfig> = _config.asStateFlow()

    fun update(transform: (HxmvConfig) -> HxmvConfig) {
        val next = transform(_config.value).let {
            it.copy(baseUrl = it.baseUrl.trim().trimEnd('/'), token = it.token.trim())
        }
        prefs.edit()
            .putString(KEY_BASE, next.baseUrl)
            .putString(KEY_TOKEN, next.token)
            .putString(KEY_PROVIDER, next.provider.name)
            .putString(KEY_PROJECT, next.project)
            .apply()
        _config.value = next
    }

    private fun load(): HxmvConfig {
        val provider = prefs.getString(KEY_PROVIDER, null)?.let { name ->
            runCatching { HxmvProvider.valueOf(name) }.getOrNull()
        } ?: HxmvProvider.LOCAL
        return HxmvConfig(
            baseUrl = prefs.getString(KEY_BASE, HxmvConfig.DEFAULT_BASE)
                ?.takeIf { it.isNotBlank() } ?: HxmvConfig.DEFAULT_BASE,
            token = prefs.getString(KEY_TOKEN, "").orEmpty(),
            provider = provider,
            project = prefs.getString(KEY_PROJECT, "").orEmpty(),
        )
    }

    companion object {
        private const val PREFS = "hermchat_hxmv"
        private const val KEY_BASE = "base_url"
        private const val KEY_TOKEN = "token"
        private const val KEY_PROVIDER = "provider"
        private const val KEY_PROJECT = "project"
    }
}
