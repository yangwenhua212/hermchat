# hermchat

[![License: AGPL v3](https://img.shields.io/badge/License-AGPL_v3-blue.svg)](LICENSE)

**HxSync** — 把你自己的 AI Agent 装进口袋。

> **内部自用 / 开发预览**：优先打磨「连电脑 Agent」与「远程大脑 + 本机工具」。不对外分发时，按自己的机型改即可。

| | 名称 |
|--|------|
| 仓库 / 工程 | `hermchat` |
| App 显示名 | **HxSync** |
| 应用 ID | `com.eraherm.hermchat` |
| 当前版本 | **0.1.13** |
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

## 四种能力档位

| # | 模式 | 说明 |
|---|------|------|
| ③ | **远端 Agent（主力）** | WebSocket「连电脑上的助手」/ Hermes HTTP；完整 Agent 引擎 |
| ④ | **端侧网关** | 本地小模型 + API 混合路由；本机闹钟/日历（[REMOTE_BRAIN_LOCAL_TOOLS.md](docs/REMOTE_BRAIN_LOCAL_TOOLS.md)） |
| ② | **纯 API 保底** | HTTP 兼容，只聊天 |
| ① | **本地小模型** | Gemma **270M** 按需下载；无网/隐私 |

主线打磨 **③**；出门用 **④**；纯闲聊可 **②/①**。配置助手一句话配。总览：[docs/PRODUCT.md](docs/PRODUCT.md) · [docs/CONNECT_AGENTS.md](docs/CONNECT_AGENTS.md)。

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
