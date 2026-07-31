# hermchat

[![License: AGPL v3](https://img.shields.io/badge/License-AGPL_v3-blue.svg)](LICENSE)

**HxSync** — 把你自己的 AI Agent 装进口袋。

> 通用个人 Agent **口袋客户端**：远程连接、直连 API，以及（规划中）手机本地运行时——同一 App 里对话，界面听你的。协议兼容常见实现，**不绑死单一品牌**。

| | 名称 |
|--|------|
| 仓库 / 工程 | `hermchat` |
| App 显示名 | **HxSync** |
| 应用 ID | `com.eraherm.hermchat` |
| 当前版本 | **0.1.2** |

## 三种模式（分阶段）

| 模式 | 阶段 | 说明 |
|------|------|------|
| 远程 Agent（WebSocket） | Phase A ✅ | 连电脑/云端已运行的 Agent |
| 直连 API（HTTP 兼容） | Phase A ✅ | DeepSeek / OpenAI / Ollama 等 |
| 本地运行时 | Phase B 🚧 | 手机内推理 / 本地编排，见 [docs/LOCAL_MODEL.md](docs/LOCAL_MODEL.md) |

产品全文：[docs/PRODUCT.md](docs/PRODUCT.md)。接入：[docs/CONNECT_AGENTS.md](docs/CONNECT_AGENTS.md)。

## 五分钟上手（真机 + 演示 Bridge）

### 1. 装 APK

```bash
./gradlew :app:assembleRelease
```

APK：`app/build/outputs/apk/release/app-release.apk`。见 [docs/RELEASE.md](docs/RELEASE.md)。

### 2. 电脑起演示 Bridge

同一 Wi‑Fi；`ipconfig` 查局域网 IP。

```bash
pip install websockets
python scripts/demo_bridge.py
```

### 3. 手机配置

选 **WebSocket** → 填终端打印的 `ws://…:8765/ws`（真机不用 `10.0.2.2`）→ 测试 → 聊天。

扫码：[docs/SETUP_QR.md](docs/SETUP_QR.md)。验收：[docs/ACCEPTANCE.md](docs/ACCEPTANCE.md)。

---

## EraHerm 分工

| 仓库 | 角色 |
|------|------|
| [eraherm-memory](https://github.com/yangwenhua212/eraherm-memory) | 记忆增强 |
| **hermchat（本仓库）** | 口袋客户端：远程 / 直连 API；Phase B 可选本地运行时；唤醒与本机工具 |

**许可**： [AGPL-3.0](LICENSE)；商用见 [COMMERCIAL.md](COMMERCIAL.md)。

## 当前状态

Phase A 主干已可用；Step 12 离线唤醒（sherpa KWS）已接入。进度：[docs/ROADMAP.md](docs/ROADMAP.md)。UI 规范：[docs/UI.md](docs/UI.md)。

## 本地开发

```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

需 Android SDK（`local.properties`）。Android Studio 打开仓库根目录，运行 `app`。

## 许可

默认 **[AGPL-3.0](LICENSE)** © HermChat Authors。闭源商用见 **[COMMERCIAL.md](COMMERCIAL.md)**。
