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
- ✅ MCP 兼容记忆内核
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
- [ ] Hermes 端适配器（将手机工具注册为可调用远端能力）
- [ ] Demo 验收：电脑发“定闹钟”，手机弹确认卡，执行成功回传
### 预计时间
2～3 周（取决于 Hermes 端适配工作量）
---
## Phase 3: Multi-device Gateway 📋 (v0.5.0)
**目标**：让 HxSync 成为个人 AI 网络中心。
### 计划交付
- [ ] 多设备绑定（电脑、NAS、树莓派同时接入）
- [ ] 设备间任务路由（自动选择最佳执行设备）
- [ ] 主动提醒（AI 按日程/习惯主动推送建议）
- [ ] Daily AI（每日摘要、任务建议、计划生成）
- [ ] 端侧 Gateway 策略引擎升级（成本/延迟/隐私权重配置）
---
## Phase 4: Personal AI OS 🎯 (v1.0.0)
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
| v0.3.x | Bridge Enhance | 工具生态扩展 | 📋 待规划 |
| v0.5.0 | Multi-Gateway | 多设备网络 | 📋 待启动 |
