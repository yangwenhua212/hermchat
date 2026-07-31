# hermchat

[![License: AGPL v3](https://img.shields.io/badge/License-AGPL_v3-blue.svg)](LICENSE)

**HxSync** — 把你自己的 AI Agent 装进口袋。

> 不是企业 IM 里的机器人插件，而是**个人拥有的 Agent 客户端**：三步配好、界面听你的、多个 Agent 像切账号一样切换。

| | 名称 |
|--|------|
| 仓库 / 工程 | `hermchat` |
| App 显示名 | **HxSync** |
| 应用 ID | `com.eraherm.hermchat` |

## EraHerm 生态分工

多数 Agent 自己就会记事、调工具。EraHerm 不重复造「脑子里的通用能力」，而是补两块个人侧体验：

| 仓库 | 角色 | 一句话 |
|------|------|--------|
| [eraherm-memory](https://github.com/yangwenhua212/eraherm-memory) | **记忆增强** | 可嵌入的记忆内核：记得更准、纠正能进化 |
| **hermchat（本仓库）** | **口袋里的家** | 手机客户端：连得上、喊得醒、确认后动手（日历等） |

一句话：**memory 增强「记得」；hermchat 提供「住在手机里、用得顺手」。** 兼容常见 WebSocket / OpenAI 兼容 HTTP 端点，不绑死某一家 Agent。

产品定位全文见 [docs/PRODUCT.md](docs/PRODUCT.md)。

**许可**：默认 [AGPL-3.0](LICENSE)；闭源商用见 [COMMERCIAL.md](COMMERCIAL.md)。

---

## 要解决什么

飞书一类工具为企业 IT 设计：机器人藏在工作台 / 应用管理 / 开发者后台，还要配白名单、事件订阅、回调地址。个人用户只想「把自己的 Agent 装进手机用」——却要买下一栋楼。

**HermChat 只做一件事：** 用最简单的方式，把**你自己的** AI Agent 装进手机，并在你确认后调用本机能力。

| 原则 | 做法 |
|------|------|
| 配置在 App 内 | 不跳转网页；不问 IP 白名单、回调地址 |
| 三步五分钟 | 选类型 → 填地址（可测连）→ 起名字 |
| 界面归用户 | 快捷指令、气泡主题、输入偏好、头像可定制 |
| Agent 是主角 | 多 Agent 顶栏切换；IM 只是交互壳 |

闭环（MVP）：`唤醒 / 打字 → Agent 处理 → 手机工具（确认后执行）`。

---

## 配置体验（目标形态）

```
Step 1  选类型：WebSocket │ HTTP 兼容 │ 自定义
Step 2  填地址：ws://… 或 http://…  [测试]
Step 3  起名字：我的助手（可选）
        → 连接成功，开始聊天
```

进阶：本机预设自动探测、扫码 / 粘贴导入配置。

多 Agent：顶栏下拉切换「家里的 / 工作的」，点一下就换，不用重配。

---

## 当前状态

分步进度见 [docs/ROADMAP.md](docs/ROADMAP.md)。

✅ **主路径 Step 0–9 已完成**：配置（含探测/扫码）→ 对话 → 多 Agent → 唤醒/ASR → 日历确认 → 快捷指令 → 验收/Release/出码文档。

下一步建议：按 [docs/ACCEPTANCE.md](docs/ACCEPTANCE.md) 真机跑通；需要时再开本机工具扩展或离线唤醒。

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

# 内测 Release APK（无正式签名时用 debug 签名）
./gradlew :app:assembleRelease
```

电脑生成配置二维码：见 [docs/SETUP_QR.md](docs/SETUP_QR.md)。打包装机见 [docs/RELEASE.md](docs/RELEASE.md)。

用 Android Studio 打开本仓库根目录，运行 `app`。桌面显示名：**HxSync**。

## 许可

默认 **[AGPL-3.0](LICENSE)** © HermChat Authors。

- 开源使用（含网络服务须提供对应源码）：遵守 AGPL-3.0  
- 闭源 / 专有商用：见 **[COMMERCIAL.md](COMMERCIAL.md)**  
