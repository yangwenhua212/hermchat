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

**聊天页：** 顶栏下拉管理 Agent；历史列表**只列当前 Agent 的会话**；「新建对话」换本地会话 + 服务端 Session；齿轮改主题/朗读/输入/快捷指令；气泡喇叭可朗读。连接/发送失败显示中文短提示（如「找不到服务器」），不展示英文异常原文。

**朗读：** 设置 → 朗读。引擎可选 **系统** / **云端** / **自动**（默认）。云端走当前 Agent 的 `POST /v1/audio/speech`（OpenAI 兼容）；Hermes 若未开放该接口会回退系统 TTS。系统侧需装中文语音包（设置里可跳转）。Hermes 电脑本机出声不会自动传到手机，需手机侧系统或云端接口。

**智能配置：** 首次安装或「添加 Agent」默认进入**配置助手**对话。  
- Hermes HTTP：`连一下 47.x.x.x` → 再直接粘贴 Key（不必写 `Key:`）→ 回显确认 → 测连保存  
- **WebSocket 远程 Agent**：`连电脑上的助手` 自动局域网探测；或 `websocket 192.168.1.8` 扫该主机常见端口；也可直接发 `ws://…`  
输入框上方可随时点「手动配置」走表单。

**后台连接：** 连上 WebSocket / Hermes 后，通知栏可出现「HxSync 保持连接」（`BridgeKeepAliveService`），连接挂在 Application 级，**回桌面 / 退出聊天页不会故意关掉**。仍可能被系统强杀；**划掉多任务**后需重开。  

**后台唤醒问云端：** 开启唤醒监听且「识别后自动发送」时，进后台仍可喊词问当前 Agent（聊天页在走同一会话，否则独立请求 + 通知摘要）。划掉进程后无法工作。

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

OpenAI、DeepSeek、Ollama 兼容端口等——**不经 WebSocket**，App 直调 HTTP。

### 要本机闹钟 / 日历（推荐）

选 **端侧网关（④）**，不要选「HTTP 兼容」。见 [REMOTE_BRAIN_LOCAL_TOOLS.md](REMOTE_BRAIN_LOCAL_TOOLS.md)。

1. 添加 Agent → **端侧网关**  
2. API Base / Key / 模型；可选本地兜底模型  
3. 测连 → 聊天 → 确认卡后操作手机  

### 只要纯聊天（②）

1. 添加 Agent → **HTTP 兼容**（默认**不开**本机工具）  
2. Base URL（如 `https://api.deepseek.com`）+ Key + 模型 id →「测试」→ 聊天  

「测试」会请求 `{base}/v1/chat/completions`（失败时再试 `/v1/models`）。

### Hermes HTTP 会话（重要）

不带 `X-Hermes-Session-Id` 时，部分 Hermes API 会用隐式规则绑死会话，上下文只增不减，越聊越慢甚至流式断连。

App（≥0.1.9）行为：

- 每次请求带独立的 `X-Hermes-Session-Id`
- 顶栏「新建对话」、切换 Agent、当前会话本地消息约 ≥20 条 → **换新 Session**（服务端上下文归零；本地旧会话保留在历史列表）
- 请求体**只发本轮** user，不把本地全量历史塞进 body（避免与服务端会话双重堆叠）
- 从历史打开旧会话时本地气泡可回看；Hermes Session **不**自动恢复（再发送相当于新服务端上下文）

---

## C. 演示 Bridge

```bash
pip install websockets
python scripts/demo_bridge.py
```

仅验证 App，不是完整 Agent。扫码：[SETUP_QR.md](SETUP_QR.md)。
