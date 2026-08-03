# HxSync — 把你自己的 AI Agent 装进口袋

[![License: AGPL v3](https://img.shields.io/badge/License-AGPL_v3-blue.svg)](LICENSE)

> **一句话定位**：HxSync 是一个「AI 能力聚合客户端」——它不生产 AI 能力，而是让你自由选择谁来提供 AI 能力，并把这些能力装进口袋。

> **内部自用 / 开发预览**：优先打磨「连电脑 Agent」与「远程大脑 + 本机工具」。不对外分发时，按自己的机型改即可。

## 项目信息

| | 名称 |
|--|------|
| 仓库 / 工程 | `hermchat` |
| App 显示名 | **HxSync** |
| 应用 ID | `com.eraherm.hermchat` |
| 当前版本 | **0.1.17** |
| 许可 | **AGPL-3.0**（App 内「关于」页含源代码链接） |

## 四种能力档位

HxSync 内置四档模式，用户可按场景自由切换：

| # | 模式 | 说明 | 适用场景 |
|---|------|------|----------|
| ③ | **远端 Agent（主力）** | WebSocket「连电脑上的助手」/ Hermes HTTP；完整 Agent 引擎 + 工具链 | 在家 / 办公室，主力智能体 |
| ④ | **端侧网关** | 本地 + API 混合路由：**自动**判复杂度或设置里**手动**优先本地/云端；本机闹钟/日历须用户确认 | 出门在外、电脑未开 |
| ② | **纯 API 保底** | HTTP 兼容，只聊天（不配本机工具） | 没有自建服务时的保底 |
| ① | **本地小模型** | Gemma 270M 等 `.task` 按需下载；资源库管理权重后选用到「本地」Agent | 无网 / 隐私敏感 |

> **建议用法**：平时用 **③**，出门切 **④**，纯闲聊可 **②/①**。

### 资源库

顶栏切换已保存的 Agent。**资源库**（设置 / Agent 下拉）统一管理 Agent 与端侧模型：

- 下载、删除、Hugging Face（`litert-community`）搜索
- 选用到当前 **① / ④** Agent

配置助手可一句话配。详细文档：

- [产品总览](docs/PRODUCT.md)
- [连接 Agent](docs/CONNECT_AGENTS.md)
- [本地模型](docs/LOCAL_MODEL.md)
- [端侧网关 + 本机工具](docs/REMOTE_BRAIN_LOCAL_TOOLS.md)

## 安全与隐私（必读）

- **`ws://` / `http://` 仅限同一 Wi‑Fi 局域网演示。** 公网或传密钥请用 **`wss://` / `https://`**。
- 本机工具（日历/闹钟）**必须用户点确认**后才执行。闹钟优先系统时钟/倒计时；不支持则回退本机通知。
- API Key 使用 `EncryptedSharedPreferences` 加密存储。

上手：[CONNECT_AGENTS.md](docs/CONNECT_AGENTS.md) · 验收：[ACCEPTANCE.md](docs/ACCEPTANCE.md) · 安全：[SECURITY.md](docs/SECURITY.md)

## 技术架构（当前）

四档**并列**，由用户选当前 Agent；④ 只是其中一档的调度层，不是全 App 唯一中枢。

```
┌──────────────────────────────────────────────────┐
│                    HxSync App                      │
│  UI（聊天）· 确认卡 · 资源库 · 设置 · 唤醒         │
└───────────────┬──────────────┬───────────┬───────┘
                │              │           │
       ┌────────▼──────┐ ┌─────▼─────┐ ┌───▼────────────┐
       │ ① 本地小模型   │ │ ② 纯 API  │ │ ③ 远端 Agent   │
       │ Gemma .task   │ │ DeepSeek… │ │ WS / Hermes    │
       └───────────────┘ └───────────┘ └────────────────┘
                │
       ┌────────▼──────────────────────────────────────┐
       │ ④ 端侧网关（可选）                              │
       │ 路由：自动 / 优先本地 / 优先云端                │
       │ 本地兜底 + API + 本机工具（确认后执行）          │
       └───────────────────────────────────────────────┘
```

## 自己构建试用（推荐）

```bash
# 需本机已有 hermchat-release.jks + keystore.properties（勿提交）
./gradlew :app:assembleRelease
```

APK：`app/build/outputs/apk/release/app-release.apk`（**正式 release 签名**）。  
签名与备份：[docs/RELEASE.md](docs/RELEASE.md)。安装时允许「未知来源」。

| 情况 | 要不要先卸载 |
|------|----------------|
| 连续装同一套 **release** 签名包（如 GitHub 预览 APK 互升） | **不用**，直接覆盖 |
| 以前装过 **debug** 包，或换过签名密钥 | **必须先卸载**再装 |

预览包也可从 [Releases](https://github.com/yangwenhua212/hermchat/releases) 下载。

### 本地开发

```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
./gradlew :app:testDebugUnitTest
```

## EraHerm 分工

| 仓库 | 角色 |
|------|------|
| [eraherm-memory](https://github.com/yangwenhua212/eraherm-memory) | 记忆增强 |
| **hermchat（本仓库）** | 口袋客户端 |

商用闭源见 [COMMERCIAL.md](COMMERCIAL.md)。进度：[docs/ROADMAP.md](docs/ROADMAP.md)。

## 远期规划（当前版本未实现）

以下**不是**当前预览版（0.1.17）能力，写在这里只为讲清方向。

### 设备指纹 / 订阅云端（远期）

> 「不是来管理用户账号，而是管理设备。」

规划中可能采用**设备指纹**而非传统登录（邮箱/密码）：

- App 生成并本地加密保存 `device_id`，请求云端时携带
- 云端用 `device_id` 记订阅状态、服务商偏好等
- 卸载重装后订阅在云端可保留，但本机 `device_id` 会变，需重新配置连接

**现状**：无账号、无 `device_id`、无云端订阅；配置与 Key 仅存本机。

### 服务商市场（远期）

- **用户**：浏览服务商列表，点选连接，少填 IP/Key  
- **服务商**：部署 Hermes 兼容服务后可申请入驻  
- **平台**：维护目录与接口规范  

**「手动配置」会永久保留**，与市场并行。现阶段重点仍是四档切换与聊天体验。

## FAQ

**Q：一定要自己部署 Hermes 才能用吗？**  
A：不一定。可用 **② 纯 API** 直连 DeepSeek/OpenAI 等，或 **① 本地** / **④ 网关**。

**Q：端侧网关路由是自动还是手动？**  
A：都支持。自动按复杂度选本地/云端；设置 → 端侧网关 可选手动「优先本地 / 优先云端」。

**Q：服务商市场 / 设备指纹什么时候上线？**  
A：远期规划，无时间表。当前不依赖它们也能完整使用四档。

**Q：我能在 HxSync 上对外提供自己的 AI 服务吗？**  
A：目前只适合给自己或朋友**手动配置**。市场入驻属远期。

**Q：会一直开源吗？**  
A：App 本体保持 **AGPL-3.0**。商业化若涉及官方云服务，不削弱开源客户端的核心能力。

## 文档索引

| 文档 | 说明 |
|------|------|
| [PRODUCT.md](docs/PRODUCT.md) | 产品与四档设计 |
| [CONNECT_AGENTS.md](docs/CONNECT_AGENTS.md) | 连接配置 |
| [LOCAL_MODEL.md](docs/LOCAL_MODEL.md) | 本地模型 / 资源库 |
| [REMOTE_BRAIN_LOCAL_TOOLS.md](docs/REMOTE_BRAIN_LOCAL_TOOLS.md) | 端侧网关 + 本机工具 |
| [ACCEPTANCE.md](docs/ACCEPTANCE.md) | 真机日记 / 验收 |
| [SECURITY.md](docs/SECURITY.md) | 安全 |
| [RELEASE.md](docs/RELEASE.md) | 签名与发版 |
| [ROADMAP.md](docs/ROADMAP.md) | 开发步骤 |
| [COMMERCIAL.md](COMMERCIAL.md) | 商用说明 |

## 许可

**[AGPL-3.0](LICENSE)** © HermChat Authors。分发 APK 时须提供对应源代码获取方式（App「关于」页已含仓库链接）。
