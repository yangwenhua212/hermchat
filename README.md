# hermchat

[![License: AGPL v3](https://img.shields.io/badge/License-AGPL_v3-blue.svg)](LICENSE)

**HxSync** — 把你自己的 AI Agent 装进口袋。

> 不是企业 IM 里的机器人插件，而是**个人拥有的 Agent 客户端**：三步配好、界面听你的、多个 Agent 像切账号一样切换。

| | 名称 |
|--|------|
| 仓库 / 工程 | `hermchat` |
| App 显示名 | **HxSync** |
| 应用 ID | `com.eraherm.hermchat` |
| 当前版本 | **0.1.1** |

## 五分钟上手（真机 + 演示 Bridge）

适合：先验证 App，不必接真实 Agent。

### 1. 装 APK

```bash
./gradlew :app:assembleRelease
```

把 `app/build/outputs/apk/release/app-release.apk` 拷到手机安装（或 `adb install -r …`）。详见 [docs/RELEASE.md](docs/RELEASE.md)。

### 2. 电脑起演示 Bridge

手机与电脑同一 Wi‑Fi。查电脑局域网 IP（Windows：`ipconfig`）。

```bash
pip install websockets
python scripts/demo_bridge.py
```

终端会打印类似：`ws://192.168.x.x:8765/ws`。

### 3. 手机里配置

1. 打开 **HxSync** → 选 **WebSocket**
2. 地址填终端打印的 `ws://…:8765/ws`（**真机不要用** `10.0.2.2`，那是模拟器专用）
3. 点 **测试** → 成功后起名 → 开始聊天，发「你好」

出二维码 / 粘贴导入：[docs/SETUP_QR.md](docs/SETUP_QR.md)。验收清单与踩坑：[docs/ACCEPTANCE.md](docs/ACCEPTANCE.md)。

接真实 Agent 时：换成你的 WebSocket 或 OpenAI 兼容 HTTP 地址即可，协议见 [docs/BRIDGE_PROTOCOL.md](docs/BRIDGE_PROTOCOL.md)。

---

## EraHerm 生态分工

多数 Agent 自己就会记事、调工具。EraHerm 不重复造「脑子里的通用能力」，而是补两块个人侧体验：

| 仓库 | 角色 | 一句话 |
|------|------|--------|
| [eraherm-memory](https://github.com/yangwenhua212/eraherm-memory) | **记忆增强** | 可嵌入的记忆内核：记得更准、纠正能进化 |
| **hermchat（本仓库）** | **口袋里的家** | 手机客户端：连得上、喊得醒、确认后动手（日历等） |

一句话：**memory 增强「记得」；hermchat 提供「住在手机里、用得顺手」。** 兼容常见 WebSocket / OpenAI 兼容 HTTP 端点，不绑死某一家 Agent。

产品定位：[docs/PRODUCT.md](docs/PRODUCT.md)。进度：[docs/ROADMAP.md](docs/ROADMAP.md)。

**许可**：默认 [AGPL-3.0](LICENSE)；闭源商用见 [COMMERCIAL.md](COMMERCIAL.md)。

---

## 要解决什么

飞书一类工具为企业 IT 设计：机器人藏在工作台 / 应用管理 / 开发者后台，还要配白名单、事件订阅、回调地址。个人用户只想「把自己的 Agent 装进手机用」——却要买下一栋楼。

**HermChat 只做一件事：** 用最简单的方式，把**你自己的** AI Agent 装进手机，并在你确认后调用本机能力。

| 原则 | 做法 |
|------|------|
| 配置在 App 内 | 不跳转网页；不问 IP 白名单、回调地址 |
| 三步五分钟 | 选类型 → 填地址（可测连）→ 起名字 |
| 界面归用户 | 快捷指令、输入偏好可定制 |
| Agent 是主角 | 多 Agent 顶栏切换；IM 只是交互壳 |

闭环：`唤醒 / 打字 → Agent 处理 → 手机工具（确认后执行）`。

## 配置形态

```
Step 1  选类型：WebSocket │ HTTP 兼容 │ 自定义
Step 2  填地址：ws://… 或 http://…  [测试] / 自动探测 / 扫码
Step 3  起名字：我的助手（可选）
```

## 当前状态

✅ **Step 0–10**：主功能 + 真机上手文档 + `0.1.1` 内测包路径就绪。

下一步可选：接真实 Agent、第二个本机工具、或离线唤醒（sherpa/Vosk）。

## 技术栈

| 模块 | 方案 |
|------|------|
| UI | Jetpack Compose |
| 网络 | OkHttp WebSocket / OpenAI 兼容 HTTP SSE |
| 存储 | Room + SharedPreferences |
| 唤醒 / ASR | 系统 SpeechRecognizer（可换 sherpa-onnx / Vosk） |
| 许可 | AGPL-3.0 + 商业双轨 |

## 本地开发

```bash
# 需本机 Android SDK（local.properties 里 sdk.dir）
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

用 Android Studio 打开本仓库根目录，运行 `app`。桌面显示名：**HxSync**。

## 许可

默认 **[AGPL-3.0](LICENSE)** © HermChat Authors。

- 开源使用（含网络服务须提供对应源码）：遵守 AGPL-3.0  
- 闭源 / 专有商用：见 **[COMMERCIAL.md](COMMERCIAL.md)**  
