# 怎么接入 Agent / 大模型

HxSync **不内置大模型**。它连你电脑（或云端）上已在跑的 Agent / API。

## 一图看懂

| 你想接什么 | App 里选 | 填什么 |
|------------|----------|--------|
| Hermes 一类本地 Agent（WebSocket） | **WebSocket** | `ws://电脑IP:端口/路径` |
| OpenAI / DeepSeek / Ollama 等 HTTP | **HTTP 兼容** | `https://…` 或 `http://局域网IP:端口` + API Key（若需要）+ 模型名 |
| 本仓库演示回声 | WebSocket | 先跑 `python scripts/demo_bridge.py`，填终端打印的地址 |

真机：**不要用** `10.0.2.2`（那是模拟器访问电脑）。用 `ipconfig` 查电脑局域网 IP，手机与电脑同一 Wi‑Fi。

---

## A. 接 Hermes（或同类 WebSocket Agent）

1. 在电脑上按 Hermes 文档把 Agent / Gateway **启动起来**，确认 WebSocket 已监听。  
   常见地址形态（以你实际为准）：
   - `ws://192.168.x.x:8765/ws`（简易 Bridge）
   - `ws://192.168.x.x:****/api/ws`（部分官方网关）
2. 打开 HxSync → 顶栏下拉当前名字 → **添加新 Agent**（或首次安装走三步配置）
3. 选 **WebSocket** → 填上面的 `ws://…` → **测试** → 起名 → 完成
4. 聊天页发「你好」，应有流式回复

协议细节见 [BRIDGE_PROTOCOL.md](BRIDGE_PROTOCOL.md)。若测连失败：防火墙放行端口、确认同网、地址路径是否含 `/api/ws`。

**Agent 管理入口**：顶栏 **Agent 名称下拉**（添加 / 切换）。右上角齿轮是**聊天主题与输入偏好**，不是加 Agent。

---

## B. 接大模型 API（OpenAI 兼容）

适用于：OpenAI、DeepSeek、硅基流动、本地 Ollama（OpenAI 兼容端口）等。

1. HxSync → **添加新 Agent** → 选 **HTTP 兼容**
2. **地址**示例：
   - OpenAI：`https://api.openai.com`
   - DeepSeek：`https://api.deepseek.com`
   - Ollama 本机：`http://192.168.x.x:11434`
3. 填写 **API Key**（云端必填；纯本地 Ollama 可留空）
4. 填写 **模型名**（如 `gpt-4o-mini`、`deepseek-chat`、`llama3.2`）
5. **测试** → 起名 → 聊天

App 会请求：`POST {地址}/v1/chat/completions`（若你已写全路径到 `/v1/chat/completions` 则不再拼接）。

---

## C. 仅想先看 App（演示 Bridge）

```bash
pip install websockets
python scripts/demo_bridge.py
```

按终端提示的 `ws://…:8765/ws` 填进 WebSocket 配置即可。这只是回声演示，不是 Hermes。

扫码导入见 [SETUP_QR.md](SETUP_QR.md)。
