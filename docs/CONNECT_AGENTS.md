# 怎么接入 Agent / 大模型（Phase A）

HxSync 是**通用口袋客户端**（品牌表述 B）：兼容远程 Agent、直连 API，以及 **本地运行时**（见 [LOCAL_MODEL.md](LOCAL_MODEL.md)）。

产品总览：[PRODUCT.md](PRODUCT.md)。

## 一图看懂

| 模式 | App 里选 | 填什么 |
|------|----------|--------|
| 远程 Agent（电脑/云） | **WebSocket** | `ws://主机:端口/路径` |
| **Hermes（推荐少填）** | **Hermes** | **主机**（IP/域名）+ API Key + 模型名 |
| 直连 API | **HTTP 兼容** | 完整 Base URL + API Key（可选）+ 模型名 |
| 本地运行时 | **本地** | 下载令牌 → 下载模型 → 测试 |
| 演示回声 | WebSocket | `python scripts/demo_bridge.py` 打印的地址 |

真机用电脑局域网 IP，**不要用** `10.0.2.2`（模拟器专用）。

**安全：** `ws://` / `http://` **仅限同一 Wi‑Fi**。公网或传 API Key 时请用 **`wss://` / `https://`**，不要明文裸奔。

**聊天页：** 顶栏下拉管理 Agent；「新建对话」换 Session；齿轮改主题/输入/快捷指令；气泡长按可复制。

**智能配置：** 首次安装或「添加 Agent」默认进入**配置助手**对话。一句话发给它后会先回显「地址 / Key」请你确认，再测连保存；输入框上方可随时点「手动配置」走表单。

**后台唤醒问云端：** 开启唤醒监听且「识别后自动发送」打开时，喊唤醒词再说指令 → App（含进后台）会把指令发给当前 Agent；聊天页在则走同一会话，否则独立请求并在通知栏展示回复摘要。划掉进程后无法工作。

---

## A. 远程 Agent（WebSocket）

含在电脑或云端运行的 Hermes **WebSocket** Gateway、自建 Bridge 等。

1. 主机上启动 Agent / Gateway，确认 WebSocket 在听  
2. HxSync → 添加 Agent → **WebSocket** → 填 `ws://…` → 测试 → 命名  
3. 常见路径：`/ws`、`/api/ws`（以实际为准）

协议：[BRIDGE_PROTOCOL.md](BRIDGE_PROTOCOL.md)。长对话变慢时点「新建对话」强制换服务端 session。

---

## A2. Hermes HTTP（少填主机）

仅暴露 OpenAI 兼容 HTTP 的 Hermes（无公网 WS）时用这个：

1. 添加 Agent → **Hermes**  
2. **主机**只填 IP 或域名（如 `47.x.x.x`）；需要端口时写 `主机:8080`  
   App 自动拼成 `http://…`（已写 `http(s)://` 则原样使用）  
3. API Key、模型 id →「测试」→ 聊天  

也可扫码/粘贴：`{"kind":"HERMES","endpoint":"主机或URL","apiKey":"…"}`。

---

## B. 直连 API（OpenAI 兼容）

OpenAI、DeepSeek、Ollama 兼容端口等——**不经 WebSocket**，App 直调 HTTP。需要自己写完整 Base URL 时用 **HTTP 兼容**（不要与上面的 Hermes 快捷项混淆）。

1. 添加 Agent → **HTTP 兼容**  
2. 地址填 **Base URL**（不要漏端口；若服务在 80 端口可写 `http://主机`）  
   示例：`https://api.deepseek.com`、`http://192.168.x.x:11434`  
3. API Key、**API 用的模型 id**（不是界面显示名）→ 点「测试」→ 聊天  

「测试」会请求 `{base}/v1/chat/completions`（失败时再试 `/v1/models`），并显示具体原因（超时 / Key 无效 / 模型名等）。

请求：`POST {base}/v1/chat/completions`（若已写全路径则不拼接）。

### Hermes HTTP 会话（重要）

不带 `X-Hermes-Session-Id` 时，部分 Hermes API 会用隐式规则绑死会话，上下文只增不减，越聊越慢甚至流式断连。

App（≥0.1.9）行为：

- 每次请求带独立的 `X-Hermes-Session-Id`
- 顶栏「新建对话」、切换 Agent、本地消息约 ≥20 条 → **换新 Session**（服务端上下文归零）
- 请求体**只发本轮** user，不把本地全量历史塞进 body（避免与服务端会话双重堆叠）

---

## C. 演示 Bridge

```bash
pip install websockets
python scripts/demo_bridge.py
```

仅验证 App，不是完整 Agent。扫码：[SETUP_QR.md](SETUP_QR.md)。
