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

## 刻意未强制

- 禁止明文 `ws://` / `http://`：会打断同一 Wi‑Fi 接电脑的五分钟上手路径  
- 自签证书向导：尚未做 UI；生产环境请用正规证书  

## 仍待加强

- 本地大模型低内存卸载策略  
- UI / 仪器测试（Espresso）  
- 证书 pinning（如需要）
