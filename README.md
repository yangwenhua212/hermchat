# hermchat

[![License: AGPL v3](https://img.shields.io/badge/License-AGPL_v3-blue.svg)](LICENSE)

**手机端 Hermes 语音助手（MVP 开发中）**

> 自定义 / 预设唤醒 → 语音或文字指令 → Hermes 处理 → 手机工具执行（需用户确认）

| | 名称 |
|--|------|
| 仓库 / 工程 | `hermchat` |
| App 显示名 | **HxSync** |
| 应用 ID（规划） | `com.eraherm.hermchat` |

与 [eraherm-memory](https://github.com/yangwenhua212/eraherm-memory) 同属 EraHerm 生态：记忆内核管「记得住」，本仓库管「喊得醒、办得成」。

**许可**：默认 [AGPL-3.0](LICENSE)；闭源商用见 [COMMERCIAL.md](COMMERCIAL.md)。

---

## 状态

🚧 脚手架阶段。目标：30 天内产出可安装 APK，跑通：

1. 文字流式对话 + 本地持久化  
2. 离线唤醒（预设词优先）+ ASR  
3. 日历等工具调用 + 确认卡片  

## 技术栈（规划）

| 模块 | 方案 |
|------|------|
| UI | Jetpack Compose |
| 网络 | OkHttp WebSocket → Hermes Bridge |
| 存储 | Room + EncryptedSharedPreferences |
| 唤醒 / ASR | 开源栈（如 sherpa-onnx / Vosk） |
| 许可 | AGPL-3.0 + 商业双轨 |

## 本地开发

Android 工程将置于本仓库根目录（Gradle）。配置与编译说明随首版可运行空壳补齐。

## 许可

默认 **[AGPL-3.0](LICENSE)** © HermChat Authors。

- 开源使用（含网络服务须提供对应源码）：遵守 AGPL-3.0  
- 闭源 / 专有商用：见 **[COMMERCIAL.md](COMMERCIAL.md)**  
