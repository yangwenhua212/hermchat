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

产品原则见 [PRODUCT.md](PRODUCT.md)。

唤醒说明：Step 5 使用系统 `SpeechRecognizer`（优先离线包）；接口经 `VoiceEventBus`，后续可换 sherpa-onnx / Vosk。

工具说明：`PhoneTool` + `ToolRegistry`；新工具实现接口即可注册。协议见 [BRIDGE_PROTOCOL.md](BRIDGE_PROTOCOL.md)。
