# 安全与稳定性（当前落地）

> 产品仍允许局域网 `ws://` / `http://`（真机演示需要）；公网请自行使用 `wss://` / `https://`。

## 已做

| 项 | 做法 |
|----|------|
| API Key / Agent 配置落盘 | `EncryptedSharedPreferences`（Android Keystore）；从旧明文 prefs 自动迁移一次 |
| WebSocket 保活 | OkHttp `pingInterval(30s)`；`AgentSessionHolder` 跨 Activity；可选 `BridgeKeepAliveService`（dataSync FGS）降后台被杀概率 |
| WebSocket 断线 | 指数退避自动重连（最多约 6 次）；回前台 `ensureConnected` / softRebind；**ViewModel.onCleared 不再 close** |
| HTTP / OpenAI 兼容流 | 开流前网络失败最多再试 1 次（共 2 次）；**已吐字不重试**（防叠字）；4xx 不重试；未知主机不重试；**不**因重试更换 Hermes Session-Id |
| 异步 | Coroutine / Flow；模型下载走 `Dispatchers.IO` |
| 单元测试 | 配置导入、工具解析、协议探测（`./gradlew :app:testDebugUnitTest`） |
| Release 签名 | 长期 `hermchat-release.jks`（禁止 debug 签名发版） |
| 本地 LLM | 模型按需下载；内存不足拒绝加载 |
| AGPL 声明 | App「关于」页含许可与源代码链接；附加许可与商用边界见仓库 [COMMERCIAL.md](../COMMERCIAL.md) |
| 本机工具 | 写操作确认卡（`ToolRisk.WRITE`）；`clipboard.read` 为 `READ_ONLY` 可静默；解析默认未授权；执行前写工具须 `needConfirm=true` |
| Agent 拒违法 | Prompt 硬拒绝 + 端侧 `LocalSafetyGuard`：高置信违法意图不调模型工具链，直接短拒并说明原因；防卫/知情提问不拦 |
| 网络角色 | **出站客户端**：连用户配置的 Agent/API；产品不做公网入站网关（见 COMMERCIAL.md） |

## 明文通道

- **`ws://` / `http://` 仅限同一 Wi‑Fi 演示。公网请用 `wss://` / `https://`。**  
- demo_bridge 默认明文，勿在公网传密钥。

## 刻意未强制

- 禁止明文 `ws://` / `http://`：会打断局域网五分钟上手  
- 自签证书向导：尚未做  

## 仍待加强

- 更完整的 androidTest（长连接、Keystore）  
- 证书 pinning（如需要）  
- 对外分发渠道以后再说；内部自用优先体验
