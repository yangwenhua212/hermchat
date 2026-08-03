# ④ 端侧 Agent 网关（本地 + API 混合）

App 内**轻量真 Agent**：云端 API（如 DeepSeek）可多步工具循环；本地小模型做简单兜底。闹钟/日历在本机执行且**须用户确认**，确认后把结果回灌模型再续答。可再挂 ③ 远端 Agent（顶栏切换）。

```
手机（HxSync · AgentKind.GATEWAY）
├── HybridGatewayClient
│   ├── 本地：LocalRuntimeClient（默认 Gemma 270M；AgentProfile.localModelId）
│   └── API：OpenAiCompatClient（DeepSeek 等）+ 短历史
├── GatewayRouter：自动判复杂度，或手选优先本地 / 云端
├── Agent loop（API 路径）：tool_call → 确认卡 → 执行 → 回灌 → 再推理（最多 8 步）
└── 本机手：闹钟 / 日历 / 打开链接 / 搜索 / 分享（一律确认卡）
```

**聪明优先：** 自动路由默认走云脑（DeepSeek 等）；仅极短寒暄才走本地小模型。系统提示按「手机 Agent」多步规划；工具结果回灌后可继续下一步。

本地权重：配置页下载，或 **资源库** 下载后「选用到当前」网关 Agent（详见 [LOCAL_MODEL.md](LOCAL_MODEL.md)）。

## 路由方式

设置 → **端侧网关** → 路由：

| 模式 | 行为 |
|------|------|
| **自动**（默认） | **默认云脑**；仅极短寒暄走本地；任务/提醒/搜索等一律 API；弱本地回复仍可 escalate |
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
| 中等及以上 / 含任务词 | API（聪明优先） |

**流式**：手选「优先本地」时边生成边显示；**自动**模式下本地先缓冲，确认不必 escalate 后再整段上屏，避免弱回复与 API 叠字。

气泡 provider 会显示 `网关·本地` / `网关·API`。本机工具始终走确认卡。

### 本机闹钟 / 提醒（真机要点）

- 说「N 分钟后提醒我」「明天早上 8 点叫我」等会出确认卡（④ / ① / 开工具的 Hermes）
- 确认后**优先打开系统时钟**的闹钟/倒计时界面（请在时钟 App 里点保存）；成功文案会写「已打开系统闹钟/倒计时」
- 仅当系统时钟唤不起时，才回退 **HxSync 通知提醒**——通知栏「HxSync 提醒」**不会**出现在系统闹钟列表；文案会写明这一点
- Android 13+ 回退路径需**通知权限**；精确提醒需系统「允许精确闹钟」
- **② HTTP 兼容默认不开本机工具**；要 DeepSeek+闹钟请用 **④ 端侧网关**

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

## Agent loop 行为

1. 用户发话 → 路由到 API（或本地）；立刻上屏 `LoopStep.Planning`  
2. 模型若输出 `tool_call` JSON（或端侧话术命中）→ 确认卡（中间态收起）  
3. 用户允许 → `Executing` → 本机执行 → `Observing` → 系统气泡显示结果（写操作经 `suspend` 挂起等待确认卡；`ToolRisk.READ_ONLY` 可静默，当前工具均为 WRITE）  
4. **回灌**「【本机工具结果】…」给 API（`Planning`「第 N 步」）→ 模型用一两句话收尾；若再要工具则重复 2～4  
5. 步数上限 8；超限顶栏一键切已保存的 ③（Hermes/WS），没有则「去添加」；取消确认则中止 loop（协程 resume 拒绝，不留半截状态）  


本机工具：`alarm.create` / `calendar.create` / `url.open` / `web.search` / `share.text`（均为 `WRITE`，须确认）；`clipboard.read`（`READ_ONLY`，可静默）；`clipboard.write`（`WRITE`，须确认）。  
本地路径本身不做完整 loop；续跑始终走 API（需已配 DeepSeek 等）。

## 仍可加强

- 更多本机工具；EraHerm-Memory  
- 同机 Termux Hermes 伴侣（③ localhost）  
- 按 token/耗电更细的路由  
