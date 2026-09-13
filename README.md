# HxSync — Your Personal AI Agent Hub on Android
> **把你的 AI Agent 装进口袋。**
[![License](https://img.shields.io/badge/License-AGPL%203.0-blue.svg)](LICENSE)
[![Release](https://img.shields.io/github/v/release/yangwenhua212/hermchat)](https://github.com/yangwenhua212/hermchat/releases)
HxSync 是一个**开放式 Android AI Agent 客户端**。它连接不同来源的 AI 能力——本地模型、云端 API、远程 Agent——通过**统一调度和长期记忆**，让 AI 从「聊天机器人」变成真正的**个人智能助手**。
---
## 🚀 核心能力
### 1. 四层 AI 调度系统（四档运行层）
HxSync **不绑定任何 AI 服务**。用户自由选择 AI 能力来源：
| 模式 | 说明 | 适用场景 |
| :--- | :--- | :--- |
| **① Pocket AI（本地智能）** | 运行在手机上的本地小模型（Qwen2.5 等） | 离线、隐私敏感、简单推理 |
| **② Cloud AI（云端模型）** | 连接 OpenAI/DeepSeek/Claude 等任意兼容 API | 高性能问答，BYO LLM |
| **③ Remote Brain（远程大脑）** | 连接电脑/服务器上的 Hermes Agent | 长任务、复杂推理链 |
| **④ Smart Gateway（智能网关）** | 自动根据任务复杂度路由到本地/云端/远程 | 智能兜底，未来核心调度层 |

> ⚠️ **当前 v0.1.x 为「本地优先 + 云端兜底」雏形**，全自动路由规划于 v0.2.0。

### 2. 🧠 MCP Memory（长期记忆）
基于 **MCP 风格（MCP-style）** 的记忆内核，AI 能记住你的偏好：
> **第一次**：“帮我做一份商务报价单，用简洁风格。”
> **以后**：“帮我做报价单。” → AI 自动沿用你的格式和风格。
---
## 🏗 系统架构
```
┌───────▼───────┐ ┌────────▼────────┐ ┌──────▼──────┐
│ ① Pocket AI │ │ ② Cloud AI │ │ ③ Remote │
│ (本地模型) │ │ (API 模型) │ │ Brain │
└───────────────┘ └────────────────┘ └──────┬──────┘
│
┌────────▼────────┐
│ ④ Smart Gateway │
│ (智能路由层) │
└─────────────────┘
```
---
## 📦 项目状态
**当前版本：`v0.1.x`**
| 能力 | 状态 |
| :--- | :--- |
| Android Agent 客户端 | ✅ 已完成 |
| 四层 AI 调度 | ✅ 已完成 |
| Remote Agent 连接 (WebSocket/HTTP) | ✅ 已完成 |
| Agent Loop (分析→确认→执行→观察) | ✅ 已完成 |
| MCP Memory | ✅ 已完成 |
| 本地模型支持 (资源库下载) | ✅ 已完成 |
| 多设备 Gateway | 📋 规划中 |
| Agent Marketplace | 📋 规划中 |
---
## 👨‍💻 开发者快速接入
HxSync 天然支持 **BYO Agent**（自建 Agent）。
### 接入方式
1. **部署 Hermes**（推荐）：在电脑上运行 Hermes Agent，通过 WebSocket 与 HxSync 连接。
2. **对接任意 HTTP API**：只要兼容 OpenAI Chat Completions 格式，填 URL + Key 即可。
### 协议文档
- [连接配置指南](docs/CONNECT_AGENTS.md)
- [本地模型使用](docs/LOCAL_MODEL.md)
---
## 📱 用户快速上手
1. **下载 APK**：从 [Releases](https://github.com/yangwenhua212/hermchat/releases) 下载最新版安装。
2. **选择运行模式**（顶栏下拉切换）：
- 有电脑跑 Hermes → 切 **③ Remote Brain**
- 有 API Key → 切 **② Cloud AI**
- 无网环境 → 切 **① Pocket AI**（需先下载本地模型）
- 出门在外 → 切 **④ Smart Gateway**
3. **开始对话**：像聊天一样发指令，AI 会自动调度执行。
---
## 🛣 Roadmap
| 阶段 | 版本 | 目标 |
| :--- | :--- | :--- |
| **Phase 1** | v0.1.x | 完整的 Android Agent 客户端（四层调度 + Loop + 记忆） ✅ |
| **Phase 2** | v0.5.0 | **多设备 Gateway**：连接 Windows/Linux/NAS，主动提醒/Daily AI |
| **Phase 3** | v1.0.0 | **Personal AI OS**：Agent Marketplace，家庭智能设备接入 |
---
**License**: AGPL-3.0 © HxSync Contributors
