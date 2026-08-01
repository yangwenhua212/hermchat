# hermchat

[![License: AGPL v3](https://img.shields.io/badge/License-AGPL_v3-blue.svg)](LICENSE)

**HxSync** — 把你自己的 AI Agent 装进口袋。

> **开发预览（pre-1.0）**：当前包供作者与协作者**自行试用 / 开发测试**。未宣称 1.0 稳定对外服务；商店上架以后再说。

| | 名称 |
|--|------|
| 仓库 / 工程 | `hermchat` |
| App 显示名 | **HxSync** |
| 应用 ID | `com.eraherm.hermchat` |
| 当前版本 | **0.1.9** |
| 许可 | **AGPL-3.0**（App 内「关于」页含源代码链接） |

## 自己构建试用（推荐）

```bash
# 需本机已有 hermchat-release.jks + keystore.properties（勿提交）
./gradlew :app:assembleRelease
```

APK：`app/build/outputs/apk/release/app-release.apk`（**正式 release 签名**，有效期很长）。  
签名与备份说明：[docs/RELEASE.md](docs/RELEASE.md)。

安装时允许「未知来源」。

**升级安装：**

| 情况 | 要不要先卸载 |
|------|----------------|
| 连续装同一套 **release 签名**包（如 GitHub 预览 APK 互升） | **不用**，直接覆盖 |
| 以前装过 **debug** 包，或换过签名密钥 | **必须先卸载**再装，否则装不上 |

预览包也可从 [Releases](https://github.com/yangwenhua212/hermchat/releases) 下载。签名与备份：[docs/RELEASE.md](docs/RELEASE.md)。

## 三种模式

| 模式 | 说明 |
|------|------|
| 远程 Agent（WebSocket） | 连电脑/云端 Agent |
| Hermes | 只填主机 + Key，自动拼 HTTP |
| 直连 API（HTTP 兼容） | DeepSeek / OpenAI / Ollama 等完整 Base URL |
| 本地运行时 | 编排在手机内；Gemma **按需下载**（不打进 APK）；内存不足会拒绝加载 |

聊天页：顶栏可「新建对话」（换 Hermes Session）；气泡长按可选中复制。添加 Agent 默认走**配置助手**对话（一句话自动配）。详见 [docs/CONNECT_AGENTS.md](docs/CONNECT_AGENTS.md)。

## 安全提醒（必读）

- **`ws://` / `http://` 仅限同一 Wi‑Fi 局域网演示。** 公网或传输密钥时请使用 **`wss://` / `https://`**，勿用明文通道传敏感信息。  
- 本机工具（日历/闹钟）**必须用户点确认**后才执行。闹钟优先调系统时钟/倒计时；若机型不支持则回退为本机通知提醒。  
- API Key 使用 EncryptedSharedPreferences 存储。  

上手演示：[docs/CONNECT_AGENTS.md](docs/CONNECT_AGENTS.md) · 验收：[docs/ACCEPTANCE.md](docs/ACCEPTANCE.md) · 安全：[docs/SECURITY.md](docs/SECURITY.md)

## EraHerm 分工

| 仓库 | 角色 |
|------|------|
| [eraherm-memory](https://github.com/yangwenhua212/eraherm-memory) | 记忆增强 |
| **hermchat（本仓库）** | 口袋客户端 |

商用闭源见 [COMMERCIAL.md](COMMERCIAL.md)。进度：[docs/ROADMAP.md](docs/ROADMAP.md)。

## 本地开发

```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
./gradlew :app:testDebugUnitTest
```

## 许可

**[AGPL-3.0](LICENSE)** © HermChat Authors。分发 APK 时须提供对应源代码获取方式（App「关于」页已包含仓库链接）。
