# 怎么接入 Agent / 大模型（Phase A）

HxSync 是**通用口袋客户端**（品牌表述 B）：兼容远程 Agent、直连 API，以及 **本地运行时**（见 [LOCAL_MODEL.md](LOCAL_MODEL.md)）。

产品总览：[PRODUCT.md](PRODUCT.md)。

## 一图看懂

| 模式 | App 里选 | 填什么 |
|------|----------|--------|
| 远程 Agent（电脑/云） | **WebSocket** | `ws://主机:端口/路径` |
| 直连 API | **HTTP 兼容** | Base URL + API Key（可选）+ 模型名 |
| 本地运行时 | **本地** | 下载令牌 → 下载模型 → 测试 |
| 演示回声 | WebSocket | `python scripts/demo_bridge.py` 打印的地址 |

真机用电脑局域网 IP，**不要用** `10.0.2.2`（模拟器专用）。

**安全：** `ws://` / `http://` **仅限同一 Wi‑Fi**。公网或传 API Key 时请用 **`wss://` / `https://`**，不要明文裸奔。

**Agent 管理**：顶栏名称下拉。**齿轮**：聊天主题 / 输入 / 快捷指令 / 关于。

---

## A. 远程 Agent（WebSocket）

含在电脑或云端运行的 Hermes 兼容 Gateway、自建 Bridge 等。

1. 主机上启动 Agent / Gateway，确认 WebSocket 在听  
2. HxSync → 添加 Agent → **WebSocket** → 填 `ws://…` → 测试 → 命名  
3. 常见路径：`/ws`、`/api/ws`（以实际为准）

协议：[BRIDGE_PROTOCOL.md](BRIDGE_PROTOCOL.md)。

---

## B. 直连 API（OpenAI 兼容）

OpenAI、DeepSeek、Ollama 兼容端口等——**不经完整远程 Agent**，App 直调 HTTP。

1. 添加 Agent → **HTTP 兼容**  
2. 地址示例：`https://api.deepseek.com`、`https://api.openai.com`、`http://192.168.x.x:11434`  
3. API Key、模型名 → 测试 → 聊天  

请求：`POST {base}/v1/chat/completions`（若已写全路径则不拼接）。

---

## C. 演示 Bridge

```bash
pip install websockets
python scripts/demo_bridge.py
```

仅验证 App，不是完整 Agent。扫码：[SETUP_QR.md](SETUP_QR.md)。
