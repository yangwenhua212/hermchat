# ④ 端侧 Agent 网关（本地 + API 混合）

App 内轻量调度：本地小模型 + 云端 API；闹钟/日历在本机执行且**须用户确认**。可再挂 ③ 远端 Agent（顶栏切换）。

```
手机（HxSync · AgentKind.GATEWAY）
├── HybridGatewayClient
│   ├── 本地：LocalRuntimeClient（默认 Gemma 270M；AgentProfile.localModelId）
│   └── API：OpenAiCompatClient（DeepSeek 等，model=API 名）+ 短历史
├── GatewayRouter：自动判复杂度，或手选优先本地 / 云端
└── 本机手：ToolCallParser → 闹钟 / 日历（确认卡，不静默）
```

本地权重：配置页下载，或 **资源库** 下载后「选用到当前」网关 Agent（详见 [LOCAL_MODEL.md](LOCAL_MODEL.md)）。

## 路由方式

设置 → **端侧网关** → 路由：

| 模式 | 行为 |
|------|------|
| **自动**（默认） | 按复杂度判断：简单→本地，复杂/长文→API；本地弱回复可 escalate 到 API |
| **优先本地** | 当前对话尽量走本地（未就绪才回落 API）；不因弱回复自动改云端 |
| **优先云端** | 当前对话尽量走 API（未配 API 才回落本地） |

### 自动模式细则

| 条件 | 走 |
|------|-----|
| 本地未就绪，已配 API | API |
| 未配 API，本地就绪 | 本地 |
| 短问候 / ≤28 字简单句，本地就绪 | 本地 |
| ≥80 字，或「分析/写代码/用大模型…」 | API |
| 本地回复是「未就绪」类兜底文案 | escalate → API（**不上屏弱文案**，只流式输出 API） |
| 中等长度，本地就绪 | 先本地（省钱） |

**流式**：手选「优先本地」时边生成边显示；**自动**模式下本地先缓冲，确认不必 escalate 后再整段上屏，避免弱回复与 API 叠字。

气泡 provider 会显示 `网关·本地` / `网关·API`。本机工具始终走确认卡。

## 怎么配

1. 添加 Agent → **端侧网关**（或助手说「deepseek」「端侧网关」）  
2. API Base：`https://api.deepseek.com`，**API 模型**：`deepseek-chat`，填 Key  
3. （可选）选/下载 **本地兜底**（配置页目录，或资源库「选用到当前」）→ 测 API → 保存  

纯聊天、不要本机工具 → 用 **HTTP 兼容**（②），不要选网关。

## 与四档

| 档位 | App 入口 | 端侧权重 |
|------|----------|----------|
| ① 仅本地 | 「本地」 | 资源库 / 配置页选用 → `model` |
| ② 纯 API | 「HTTP 兼容」 | 不需要 |
| ③ 远端 Agent | WebSocket / Hermes | 不需要 |
| ④ 本页 | 「端侧网关」 | 资源库选用 → `localModelId`（与 API `model` 分开） |

## ④↔③ 自动故障转移

同一轮发送：主通道抛错时，自动改用已保存的另一档（**不永久切换**当前 Agent）：

| 当前 | 优先备用 |
|------|----------|
| ④ 端侧网关 | ③ WebSocket / Hermes |
| ③ 远端 Agent | ④ 端侧网关 |
| ② 纯 API | ④ → ③ |

也可在配置里写 `fallbackAgentId` 指定备用。气泡 provider 会标 `备用·名称`。

## 仍可加强

- 按 token/耗电更细的路由  
- EraHerm-Memory 同步  
