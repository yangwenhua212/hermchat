# HxSync — Your Personal AI Agent Hub on Android
> **把你的 AI Agent 装进口袋，让 AI 拥有思考能力，也拥有你的手机能力。**
[![License](https://img.shields.io/badge/License-AGPL%203.0-blue.svg)](LICENSE)
[![Release](https://img.shields.io/github/v/release/yangwenhua212/hermchat)](https://github.com/yangwenhua212/hermchat/releases)
HxSync 是一个**开放式 Android AI Agent 客户端**。它连接不同来源的 AI 能力——本地模型、云端 API、远程 Agent、手机本地工具——通过**统一调度、长期记忆、工具调用和人工确认**，让 AI 从「聊天机器人」变成真正的**个人智能助手**。
---
## ✨ 核心理念
**传统 AI：**
```
用户 → 聊天窗口 → AI 回答（结束）
```
**HxSync：**
```
用户 → HxSync → AI Agent 思考/规划 → 调用工具 → 手机执行 → 返回结果
```
AI 不只是回答问题。它可以：
- ✅ 理解复杂任务
- ✅ 调用工具（闹钟、日历、短信、App……）
- ✅ 操作你的设备
- ✅ 记住你的习惯
- ✅ 在关键操作前**请求你确认**
---
## 🚀 核心能力
### 1. 四层 AI 调度系统（四档运行层）
HxSync **不绑定任何 AI 服务**。用户自由选择 AI 能力来源：
| 模式 | 说明 | 适用场景 |
| :--- | :--- | :--- |
| **① Pocket AI（本地智能）** | 运行在手机上的本地小模型（Qwen2.5 等） | 离线、隐私敏感、简单推理 |
| **② Cloud AI（云端模型）** | 连接 OpenAI/DeepSeek/Claude 等任意兼容 API | 高性能问答，BYO LLM |
| **③ Remote Brain（远程大脑）** | 连接电脑/服务器上的 Hermes Agent | 桌面自动化、长任务、复杂工具链 |
| **④ Smart Gateway（智能网关）** | 自动根据任务复杂度路由到本地/云端/远程 | 智能兜底，未来核心调度层 |

> ⚠️ **当前 v0.1.x 为「本地优先 + 云端兜底」雏形**，全自动路由规划于 v0.2.0。

### 2. 📱 Agent-to-Phone Bridge（手机能力桥）
**这是 HxSync 的灵魂功能：让远程 AI 安全地使用你的手机。**

> 🚧 **目标能力（规划于 v0.2.0 Agent Bridge）**：以下场景为产品目标示意，当前版本暂未开放。当前可用的是 ③ 直接连接远端 Hermes 对话。
```
Remote Brain (电脑/云端)
│
│ Tool Request
▼
HxSync Local Gateway
│
│ 确认卡弹窗
▼
用户点击 [允许]
│
▼
Android 系统执行（闹钟/日历/短信…）
│
▼
结果回传给 Remote Brain
```
**场景示例**：
> 你对电脑上的 Hermes 说：“明天早上 8 点叫我起床。”
> → Hermes 向 HxSync 发请求 `phone.alarm.create`
> → 手机弹出确认卡，显示“来自电脑的 AI 请求创建闹钟”
> → 你点【允许】，闹钟设置成功
> → 电脑回复：“已帮你设置完成。”
### 3. 🔐 Human-in-the-loop（人在回路）
**所有敏感操作必须经你确认。**
```
AI 请求 → 风险检测 → 用户确认 → 执行
```
| 风险等级 | 行为 | 确认策略 |
| :--- | :--- | :--- |
| **LOW** | 查询时间/电量/天气 | 自动执行，无需确认 |
| **MEDIUM** | 创建闹钟、日历、打开 App | **必须弹确认卡**（可勾选“本次会话记住”） |
| **HIGH** | 发送短信、删除文件、转账 | **强确认**，不可跳过，需二次点击 |
### 4. 🧠 MCP Memory（长期记忆）
基于 **MCP 风格（MCP-style）** 的记忆内核，AI 能记住你的偏好：
> **第一次**：“帮我做一份商务报价单，用简洁风格。”
> **以后**：“帮我做报价单。” → AI 自动沿用你的格式和风格。
### 5. 🛠 Personal Tool System（统一工具接口）
手机能力通过统一接口暴露给 AI：
| 类别 | 工具示例 |
| :--- | :--- |
| 系统工具 | 闹钟、日历、剪贴板、分享 |
| 通信工具 | 拨号、发邮件、发短信 |
| 应用工具 | 打开 App、开链、地图导航 |
| 记忆工具 | 读写本地 MCP 记忆 |
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
└────────┬────────┘
│
┌────────▼────────┐
│ Android Tools │
│ (手机真实能力) │
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
| Tool Calling | ✅ 已完成 |
| Human Confirmation (确认卡) | ✅ 已完成 |
| MCP Memory | ✅ 已完成 |
| 本地模型支持 (资源库下载) | ✅ 已完成 |
| **Agent Bridge（③ 调用 ④）** | 🚧 v0.2.0 进行中：客户端入站 `tool_call` 帧 + 确认卡来源标注 + `tool_result` 回传已通（待真机）；demo 见 `scripts/demo_bridge.py` |
| 多设备 Gateway | 📋 规划中 |
| Agent Marketplace | 📋 规划中 |
---
## 👨‍💻 开发者快速接入
HxSync 天然支持 **BYO Agent**（自建 Agent）。
### 接入方式
1. **部署 Hermes**（推荐）：在电脑上运行 Hermes Agent，通过 WebSocket 与 HxSync 连接。
2. **对接任意 HTTP API**：只要兼容 OpenAI Chat Completions 格式，填 URL + Key 即可。
3. **扩展本地工具**：在 Android 端实现 `@BridgeTool` 注解，自动暴露给远程 Agent。
### 协议文档
- [Agent Bridge Protocol](docs/BRIDGE_PROTOCOL.md) — 远程大脑调用手机工具的标准协议。
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
| **Phase 2** | v0.2.0 | **Agent Bridge**：远程 Agent 调用手机工具，确认卡升级 |
| **Phase 3** | v0.5.0 | **多设备 Gateway**：连接 Windows/Linux/NAS，主动提醒/Daily AI |
| **Phase 4** | v1.0.0 | **Personal AI OS**：Agent Marketplace，家庭智能设备接入 |
---
## 🤝 Philosophy（理念）
HxSync **不创造新的 AI**。
HxSync **连接你的 AI**。
- 你的模型。
- 你的数据。
- 你的设备。
- 你的 Agent。
- 你的控制。
---
**License**: AGPL-3.0 © HxSync Contributors
