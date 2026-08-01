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

App 弹出确认卡；用户点「允许」后执行，并回传：

```json
{"type":"tool_result","id":"uuid","ok":true,"content":"已创建日程…"}
```

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
