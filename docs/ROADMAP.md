# hermchat 分步计划

按顺序做；当前只推进标为进行中的一步。

| Step | 内容 | 验收 |
|------|------|------|
| 0 | Compose 空壳可编译 | ✅ |
| 1 | Room + ViewModel + 聊天真接线 | ✅ 发消息入库，杀进程重进还在 |
| 2 | App 内三步配置 Agent | ✅ 选类型 → 地址测连 → 命名 |
| 3 | Agent 流式对话 | ✅ WebSocket / HTTP SSE + 连接状态 |
| 4 | 多 Agent 切换 | ✅ 顶栏下拉切换 / 添加 |
| 5 | 唤醒 + ASR（预设词） | ✅ 预设词监听 + 麦克风 ASR |
| 6 | 日历工具 + 确认卡 | ✅ 确认后写入系统日历 |
| 7 | 快捷指令栏 + 输入偏好 | ✅ 点选插入/发送；排序可持久化；语音/文字/混合 |
| 8 | 地址自动探测 + 扫码/粘贴导入 | ✅ 探测可达端点；QR/JSON/深链填入配置 |

产品原则见 [PRODUCT.md](PRODUCT.md)。

唤醒说明：Step 5 使用系统 `SpeechRecognizer`（优先离线包）；接口经 `VoiceEventBus`，后续可换 sherpa-onnx / Vosk。

工具说明：`PhoneTool` + `ToolRegistry`；新工具实现接口即可注册。协议见 [BRIDGE_PROTOCOL.md](BRIDGE_PROTOCOL.md)。

聊天定制说明：`ChatPrefsStore` 持久化输入模式与快捷指令顺序；顶栏「调音」图标进入设置；聊天页长按指令可微调顺序。

配置进阶说明：`EndpointProbe` 探测 `10.0.2.2` 与 Wi‑Fi 网关常见端口；`AgentConfigImport` 解析二维码/粘贴内容。
