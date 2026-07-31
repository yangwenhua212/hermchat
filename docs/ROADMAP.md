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
| 12 | 离线唤醒 sherpa-onnx | ⬜ 替换系统 SpeechRecognizer；无网可唤醒 |

产品原则见 [PRODUCT.md](PRODUCT.md)。实现说明见 [NEXT_IMPL.md](NEXT_IMPL.md)（含「哪些已做好、哪些还没做」）。

- 最短上手：仓库 [README.md](../README.md#五分钟上手真机--演示-bridge)
- 接 Agent / API：[CONNECT_AGENTS.md](CONNECT_AGENTS.md)
- 真机清单：[ACCEPTANCE.md](ACCEPTANCE.md)
- 打包装机：[RELEASE.md](RELEASE.md)
- 电脑出码：[SETUP_QR.md](SETUP_QR.md)
- 演示 Bridge：`scripts/demo_bridge.py`

唤醒说明：Step 5 使用系统 `SpeechRecognizer`；接口经 `VoiceEventBus`，Step 12 换 sherpa-onnx / Vosk。

工具说明：`PhoneTool` + `ToolRegistry`；日历已接入，Step 11 扩展闹钟。协议见 [BRIDGE_PROTOCOL.md](BRIDGE_PROTOCOL.md)。

聊天定制说明：齿轮进入设置（主题色 / 气泡 / 输入 / 快捷指令）；Agent 管理在顶栏下拉。

配置进阶说明：`EndpointProbe` + 竖屏扫码 + 粘贴导入。
