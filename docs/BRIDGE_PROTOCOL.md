# Bridge 协议

HxSync 按连接类型 / URL 自动选择传输（不绑定某一家 Agent 品牌）：

| 条件 | 传输 | 说明 |
|------|------|------|
| `ws(s)://…` 且路径含 `/api/ws` | JSON-RPC WebSocket | `session.create` → `prompt.submit`；收 `message.delta` / `message.complete` |
| `ws(s)://…` 且路径含 `/v1/ws` | 消息帧 WebSocket | 发 `agent.message.send`；收 `agent.message.delta` / `done` / `error` |
| 其它 `ws(s)://…` | 简易帧 | 发 `{"type":"chat","content":"…"}`；收 `token`/`delta`/`done` |
| `http(s)://…` | OpenAI 兼容 SSE | `POST {base}/v1/chat/completions`，`stream: true` |

## 简易 WebSocket 帧（自建 Bridge 最省事）

客户端：

```json
{"type":"chat","id":"uuid","content":"你好"}
```

带图（三期附件，inline base64；过大慎用）：

```json
{
  "type": "chat",
  "id": "uuid",
  "content": "图里写了什么",
  "attachment": {
    "name": "image.jpg",
    "mime": "image/jpeg",
    "encoding": "base64",
    "data": "<base64…>"
  }
}
```

`agent.message.send` / JSON-RPC `prompt.submit` 的 params 也可带同一 `attachment` 字段。

服务端流式：

```json
{"type":"token","id":"uuid","content":"你"}
{"type":"token","id":"uuid","content":"好"}
{"type":"done","id":"uuid"}
```

错误：`{"type":"error","message":"…"}`。

## 工具调用

Agent 可在回复中夹带（或单独发送）JSON：

```json
{
  "type": "tool_call",
  "id": "uuid",
  "name": "calendar.create",
  "need_confirm": true,
  "arguments": {
    "title": "开会",
    "beginMs": "1735689600000",
    "endMs": "1735693200000",
    "description": "可选"
  }
}
```

> **v0.2.0**：App 已支持**独立 `tool_call` 帧**入站（会话级，随时可下发，不依赖某轮回复流）。收到后弹确认卡（标注「🌐 远程 AI 请求 · Agent名」），用户点「允许」后执行，并回传：

```json
{"type":"tool_result","id":"uuid","ok":true,"content":"已创建日程…"}
```

`tool_result` 会唤醒等待中的 Agent 流程（demo 见 `scripts/demo_bridge.py`，跑起来对手机说「提醒我 10 分钟后喝水」即可端到端验证）。

**App 当前可被远程调用的工具**（`need_confirm` 按风险分级，写操作一律弹确认卡）：

| 工具 | 用途 | 典型 arguments |
|------|------|----------------|
| `alarm.create` | 系统闹钟/倒计时 | `message`、`triggerMs`（毫秒） |
| `calendar.create` | 写系统日历 | `title`、`beginMs`、`endMs` |
| `web.search` | 联网搜索 | `query` |
| `app.open` | 打开应用 | `app`（如 `wechat`/`douyin`） |
| `phone.dial` | 拨号 | `number` |
| `clipboard.read` | 读剪贴板（只读静默） | — |
| `maps.search` | 地图搜索 | `query` |
| `email.compose` | 写邮件 | `to`、`subject`、`body` |

未知工具 / 未授权写操作：手机端一律拒绝并回传失败，不会静默执行。

无结构化 `tool_call` 时，客户端也会把「明天下午3点提醒我开会」这类话术解析成待确认的日历工具（本地兜底）。

模拟器访问本机：`10.0.2.2`；真机用电脑局域网 IP。App 配置页支持「自动探测」（网关 + 常见端口）与扫码/粘贴导入。

## 配置导入（二维码 / 粘贴）

推荐 JSON：

```json
{
  "v": 1,
  "kind": "WEBSOCKET",
  "endpoint": "ws://192.168.1.8:8765/ws",
  "name": "家里的助手"
}
```

`kind` 可选：`WEBSOCKET` / `HERMES` / `HTTP_COMPAT` / `CUSTOM`（旧值 `OPENCLAW`→HTTP；旧扫码 `HERMES`+`ws://` 仍按 WebSocket 解析）。

深链等价写法：

```
hxsync://agent?kind=WEBSOCKET&endpoint=ws%3A%2F%2F192.168.1.8%3A8765%2Fws&name=%E5%AE%B6%E9%87%8C
```

纯 `ws://` / `http(s)://` 地址也可直接作为二维码内容。
