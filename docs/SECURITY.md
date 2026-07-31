# 安全与稳定性（当前落地）

> 产品仍允许局域网 `ws://` / `http://`（真机演示需要）；公网请自行使用 `wss://` / `https://`。

## 已做

| 项 | 做法 |
|----|------|
| API Key / Agent 配置落盘 | `EncryptedSharedPreferences`（Android Keystore）；从旧明文 prefs 自动迁移一次 |
| WebSocket 保活 | OkHttp `pingInterval(30s)` |
| WebSocket 断线 | 指数退避自动重连（最多约 6 次）；发送前 `ensureConnected` 带短重试 |
| 异步 | Coroutine / Flow；模型下载走 `Dispatchers.IO` |
| 单元测试 | 配置导入、工具解析、协议探测（`./gradlew :app:testDebugUnitTest`） |
| Release 签名 | 长期 `hermchat-release.jks`（禁止 debug 签名发版） |
| 本地 LLM | 模型按需下载；内存不足拒绝加载 |
| AGPL 声明 | App「关于」页含许可与源代码链接 |
| 本机工具 | 一律确认卡；解析层强制 `needConfirm=true` |

## 明文通道

- **`ws://` / `http://` 仅限同一 Wi‑Fi 演示。公网请用 `wss://` / `https://`。**  
- demo_bridge 默认明文，勿在公网传密钥。

## 刻意未强制

- 禁止明文 `ws://` / `http://`：会打断局域网五分钟上手  
- 自签证书向导：尚未做  

## 仍待加强

- 更完整的 androidTest（长连接、Keystore）  
- 证书 pinning（如需要）  
- 1.0 稳定后再考虑对外分发渠道
