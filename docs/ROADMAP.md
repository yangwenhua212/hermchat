# hermchat 分步计划

按顺序做；当前只推进标为进行中的一步。

| Step | 内容 | 验收 |
|------|------|------|
| 0 | Compose 空壳可编译 | ✅ |
| 1 | Room + ViewModel + 聊天真接线 | ✅ 发消息入库，杀进程重进还在 |
| 2 | App 内三步配置 Agent | ✅ 选类型 → 填地址测连 → 命名 |
| 3 | Agent 流式对话 | ✅ WebSocket / HTTP SSE + 连接状态 |
| 4 | 多 Agent 切换 | ✅ 顶栏下拉切换 / 添加 |
| 5 | 唤醒 + ASR（预设词） | ✅ 预设词监听 + 麦克风 ASR |
| 6 | 日历工具 + 确认卡 | ✅ 确认后写入系统日历 |
| 7 | 快捷指令栏 + 输入偏好 | ✅ 点选插入/发送；排序可持久化；语音/文字/混合 |
| 8 | 地址自动探测 + 扫码/粘贴导入 | ✅ 探测可达端点；QR/JSON/深链填入配置 |
| 9 | 验收清单 + Release + 电脑出码 | ✅ 文档与脚本就绪；可 `assembleRelease` |
| 10 | 上手收口（README / 踩坑 / 0.1.1） | ✅ 五分钟真机路径；ACCEPTANCE 补坑；版本 0.1.1 |
| 11 | 第二本机工具（闹钟/提醒） | ✅ `alarm.create` + 本地话术；确认后调系统闹钟 |
| 12 | 离线唤醒 sherpa-onnx | ✅ KWS + 唤醒后短指令 ASR；点按麦克风离线识别；模型按需下载 |
| 13 | 本地运行时 Phase B | ✅ 类型「本地」+ MediaPipe/编排；模型按需下载；见 [LOCAL_MODEL.md](LOCAL_MODEL.md) |
| 14 | 离线短指令 ASR | ✅ 并入 Step 12：喊一声 → 说指令 → Transcript 自动发送 |
| 15 | 安全与稳定性收口 | ✅ 加密 Agent 存储、WS 重连、关键单测；见 [SECURITY.md](SECURITY.md) |
| 16 | 发版隐患收口 | ✅ release 长期签名、本地 LLM 内存门槛、关于页 AGPL、工具强制确认 |
| 17 | Hermes 会话 + 聊天体验 | ✅ HTTP `X-Hermes-Session-Id`；新建对话 / ≥20 条自动换会话；气泡可复制；修复编辑 Agent 跳转 |
| 18 | ④ Agent loop 初版 | ✅ API 路径 tool 确认后回灌续跑；聪明优先路由；链接/搜索/分享工具（见 [REMOTE_BRAIN_LOCAL_TOOLS.md](REMOTE_BRAIN_LOCAL_TOOLS.md)） |

## v0.3.0 目标：端侧真 Agent（进行中）

把 HxSync 从「插线板」升级为「有自己执行循环的终端机器人」——**不在 APK 内嵌 Hermes**，而是双轨：

| 轨 | 大脑 | 手脚 | 定位 |
|----|------|------|------|
| **④ 端侧 Loop** | **默认**云脑（DeepSeek 等）；可选实验「本地优先解析」（① 同类小模型试跑 tool JSON，失败本轮降云端） | 本机工具 + 确认卡 | 出门高频；实验档要快且有保底 |
| **③ 云端 Hermes** | 远端完整 Agent（WS/HTTP） | 手机可作「手脚中断响应器」（tool_call ↔ observation） | 长上下文、复杂多步、重技能 |

**灵魂问题答案：** ④ Loop 默认用 **② 同类云 API（挂在 GATEWAY 里）** 当 Planner，**不是**把 Hermes 逻辑在手机重写一遍。③ 仍是 Hermes；远期补强「云脑下发 tool → 手机执行 → observation 推回」，那是分布式协议，不是④的替代品。

### 三道鬼门关 → 路线切片

| 痛点 | v0.3 应对 |
|------|-----------|
| 超长延迟 / 后台被杀 | ✅ 每步中间态上屏（`LoopStep`：思考→执行→观察）；Loop 挂 Application 级 + 保活待加强；✅ 首包 12s 超时降级；✅ 可选 ③ 连接失败持久降级（默认关）；流式终答 |
| Context 爆炸 | ✅ 硬上限步数（现 8）；超限顶栏一键切已存 ③ / 无则去添加；历史截断待加强 |
| 确认悖论 | ✅ **分级确认**：`ToolRisk`（`clipboard.read` 静默；写操作暂停等确认） |
| 本地当 Planner 不准 | ✅ **允许但须警告**：默认仍云脑；设置「本地优先解析」开前弹窗知情；失败本轮改 API，顶栏「已改用云端」 |

### v0.3 验收（草案）

- [x] ④ Loop 中间态：`LoopStep` 上屏（分析 / 执行 / 观察）
- [x] 分级确认：`ToolRisk` + 写操作挂起等待；`clipboard.read` 静默验证 READ_ONLY
- [ ] ④：「查天气然后半小时后提醒我」能多步完成并实时显示阶段（待真机勾）
- [ ] 写操作必确认；取消后 Loop 干净结束（骨架已具备，待真机勾）
- [x] 超步数友好降级到「建议切 ③」：有已存 Hermes/WS 则一键切换；否则「去添加」
- [x] ①/本地小模型**可**驱动工具解析（实验）：开前警告后果；失败本轮降云端并一行提示（默认仍云脑 Loop）
- [x] 聊天识图一期：Composer 选图 → ②/④/Hermes HTTP vision；本地短提示；③ WS 可带 attachment
- [x] 聊天附件二期：文本注入 + PDF 首页当图
- [x] 聊天附件三期：系统分享入 + 历史大图 + Bridge `attachment`
- [x] 首包超时降级：12s 无首 token → ④ 改本地（若就绪）否则 AgentFailover
- [ ] （可选 Pro）设置里「Agent 加强」说明流量/耗电；默认对④开启聪明路由即可

产品原则见 [PRODUCT.md](PRODUCT.md)。实现备忘见 [NEXT_IMPL.md](NEXT_IMPL.md)。UI 文案见 [UI.md](UI.md)。安全见 [SECURITY.md](SECURITY.md)。

- 最短上手：仓库 [README.md](../README.md)
- 接 Agent / API：[CONNECT_AGENTS.md](CONNECT_AGENTS.md)
- 本地模型规划：[LOCAL_MODEL.md](LOCAL_MODEL.md)
- 真机清单：[ACCEPTANCE.md](ACCEPTANCE.md)
- 打包装机：[RELEASE.md](RELEASE.md)
- 电脑出码：[SETUP_QR.md](SETUP_QR.md)
- 演示 Bridge：`scripts/demo_bridge.py`

唤醒说明：Step 5 系统 `SpeechRecognizer`；Step 12 增加离线 sherpa-onnx KWS，经同一 `VoiceEngine` / `VoiceEventBus` 切换。

工具说明：`PhoneTool` + `ToolRegistry`；日历已接入；闹钟优先系统 SET_TIMER/SET_ALARM，失败回退本机通知（见 Step 17 后续补强）。协议见 [BRIDGE_PROTOCOL.md](BRIDGE_PROTOCOL.md)。

聊天定制说明：齿轮进入设置（主题色含背景 / 气泡 / 壁纸相册与搜索 / 输入 / 快捷指令）；Agent 管理在顶栏下拉；历史列表回看旧聊；「新建对话」换本地会话 + 服务端 Session。

配置进阶说明：`EndpointProbe` + 竖屏扫码 + 粘贴导入。
