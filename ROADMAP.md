# HxSync Roadmap
> 当前主版本：`v0.1.33`
> 下一个里程碑：`v0.2.0 — Agent Bridge`
---
## Phase 1: Agent Client ✅ (v0.1.x)
**目标**：成为最好用的 Android Agent 客户端。
### 已完成
- ✅ 四层 AI 调度（本地/API/远程/网关）
- ✅ Agent Loop（分析→确认→执行→观察）
- ✅ WebSocket / HTTP 连接
- ✅ 工具调用（闹钟、日历、开链、App、剪贴板、分享、地图、拨号、邮件）
- ✅ MCP 风格（MCP-style）记忆内核
- ✅ 人在回路确认卡（写操作必须确认）
- ✅ 本地模型支持（Qwen2.5 0.5B 等，资源库管理）
- ✅ 端侧网关（④ 自动/手动路由）
- ✅ 安全存储（EncryptedSharedPreferences）
### 已知优化
- 确认卡 UI/UX 微调
- 首包超时降级策略精细化
---
## Phase 2: Agent Bridge 🚧 (v0.2.0)
**目标**：让任何远程 Agent（③）能安全调用手机本地工具（④）。
### 核心交付
- [ ] 定义并实现 `Agent Bridge Protocol`（见 `docs/BRIDGE_PROTOCOL.md`）
- [ ] 手机端工具统一注解化（`@BridgeTool` + 风险分级）
- [ ] WebSocket 双向通信（支持 `invoke_tool` 请求和 `tool_result` 回传）
- [ ] 确认卡升级（显示请求来源、风险等级、允许“本次会话记住”）
- [ ] **Mock Hermes 调试工具**（命令行脚本模拟 Hermes 发 JSON 请求：先把 `@BridgeTool` 解析 → 确认卡 → 执行全链路跑通，最后再联调真 Hermes，解耦降低联调风险）
- [ ] Hermes 端适配器（将手机工具注册为可调用远端能力）
- [ ] `docs/DEVELOPMENT.md`（开发者体验）：如何新增一个手机工具（写一个类打 `@BridgeTool` 注解）、如何本地调试（模拟数据，无需连电脑）
- [ ] Demo 验收：电脑发“定闹钟”，手机弹确认卡，执行成功回传
### 预计时间
2～3 周（已通过 Mock Hermes 调试工具与真 Hermes 解耦，联调风险可控）
---
## Phase 3: Tool Ecosystem 📋 (v0.3.0)
**目标**：工具生态可扩展，协作者能低成本贡献新工具。
### 计划交付
- [ ] 现有工具（闹钟/日历/短信等）按 MCP-style 规范统一重构（统一 schema、鉴权、风险分级）
- [ ] 新增实用工具：文件管理、读取屏幕内容
- [ ] 第三方工具扩展指南落地（对齐 `docs/DEVELOPMENT.md`，社区协作者可提交新工具）
---
## Phase 4: Polishing & Performance 🔧 (v0.4.0)
**目标**：稳定版才配叫 v0.5.0——先打磨，再上多设备。
### 计划交付
- [ ] 崩溃率治理（crash-free sessions 目标 99%+）
- [ ] 弱网重连与消息可靠性（断线重连、幂等执行）
- [ ] 内存泄漏排查（长连接场景专项）
- [ ] 确认卡 UI/UX 收尾、首包超时降级策略落地
---
## Phase 5: Multi-device Gateway 📋 (v0.5.0)
**目标**：让 HxSync 成为个人 AI 网络中心。
### 计划交付
- [ ] 多设备绑定（电脑、NAS、树莓派同时接入）
- [ ] 设备间任务路由（自动选择最佳执行设备）
- [ ] 主动提醒（**基于 FCM / 厂商推送通道**：由电脑端 AI 触发推送到手机，手机不做后台轮询，规避厂商省电策略杀进程）
- [ ] Daily AI（每日摘要、任务建议、计划生成）
- [ ] 端侧 Gateway 策略引擎升级（成本/延迟/隐私权重配置）
---
## Phase 6: Personal AI OS 🎯 (v1.0.0)
**目标**：稳定、可扩展的生态级产品。
### 计划交付
- [ ] Agent Marketplace（用户浏览/连接第三方 Agent 服务）
- [ ] 订阅恢复机制（基于 Google Play 收据或本地 License 文件，**无账号体系**）
- [ ] 家庭智能设备接入（智能家居控制）
- [ ] 自定义工作流（低代码编排多 Agent 协作）
- [ ] 具身机器人探索（ROS 2 薄层接入，延续 VISION.md 中的探索方向）
---
## 版本规划总览
| 版本 | 代号 | 主题 | 状态 |
| :--- | :--- | :--- | :--- |
| v0.1.x | Agent Client | 基础 Agent 客户端 | ✅ 已完成 |
| v0.2.0 | Agent Bridge | 远程大脑 → 手机执行 | 🚧 **当前目标** |
| v0.3.0 | Tool Ecosystem | 工具生态扩展（MCP-style 重构 + 文件/屏幕工具） | 📋 待启动 |
| v0.4.0 | Polishing | 稳定与体验（崩溃/弱网/内存） | 📋 待启动 |
| v0.5.0 | Multi-Gateway | 多设备网络 | 📋 待启动 |
| v1.0.0 | Personal AI OS | 生态级产品 | 🎯 远期 |
