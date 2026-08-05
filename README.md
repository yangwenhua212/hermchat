# HxSync — 把你自己的 AI Agent 装进口袋

[![License: AGPL v3](https://img.shields.io/badge/License-AGPL_v3-blue.svg)](LICENSE)

> **一句话定位**：HxSync 是一个「AI 能力聚合客户端」——它不生产 AI 能力，而是让你自由选择谁来提供 AI 能力，并把这些能力装进口袋。

> **当前阶段**：优先打磨「连电脑 Agent」与「远程大脑 + 本机工具」两条主线，适合开发者自用尝鲜。

### 已具备

| 能力 | 说明 |
|------|------|
| **识图 / 附件** | Composer 选图或文件；② / ④ / Hermes HTTP / ③ Bridge 可识图；PDF 首页当图；系统分享入；气泡可点大图 |
| **④ Agent Loop** | 分析 → 确认 → 执行 → 观察 分阶段上屏；写操作须确认；坏 JSON 纠正；首包超时可改本地/备用 |
| **本机工具** | 闹钟/日历/开链/**联网搜索摘要**/分享/剪贴板/开应用/拨号/地图/邮件/本机记忆（见 [REMOTE_BRAIN_LOCAL_TOOLS.md](docs/REMOTE_BRAIN_LOCAL_TOOLS.md)） |
| **重能力在 ③** | 长记忆内核、远程 Shell、桌面/GUI 自动化、文生图等交给远端 Hermes/自建；手机是确认与展示 |

**尚未做 / 不抢 ③ 的活：** App 内文生图、端侧 VLM、完整 GUI 自动化、非线性对话分支。

## 项目信息

| | 名称 |
|--|------|
| 仓库 / 工程 | `hermchat` |
| App 显示名 | **HxSync** |
| 应用 ID | `com.eraherm.hermchat` |
| 当前版本 | **0.1.33** |
| 许可 | **AGPL-3.0**（App 内「关于」页含源代码链接） |

## 四种能力档位

HxSync 内置四档模式，用户可按场景自由切换：

| # | 模式 | 说明 | 适用场景 |
|---|------|------|----------|
| ③ | **远端 Agent（主力）** | WebSocket「连电脑上的助手」/ Hermes HTTP；完整 Agent 引擎 + 工具链 | 在家 / 办公室，主力智能体 |
| ④ | **端侧网关** | 本地 + API 混合；轻量 **Agent loop**（分析/执行/观察上屏）；识图附件；本机工具须确认 | 出门在外、电脑未开 |
| ② | **纯 API 保底** | HTTP 兼容，只聊天（不配本机工具） | 没有自建服务时的保底 |
| ① | **本地小模型** | Qwen2.5 0.5B 等 `.task` 按需下载（默认免 HF 令牌）；资源库管理后选用到「本地」Agent | 无网 / 隐私敏感 |

> **建议用法**：平时用 **③**，出门切 **④**，纯闲聊可 **②/①**。

### 资源库

顶栏切换已保存的 Agent。**资源库**（设置 / Agent 下拉）统一管理 Agent 与端侧模型：

- 下载、删除、Hugging Face（`litert-community`）搜索
- 选用到当前 **① / ④** Agent（端侧权重只给这两档用）

> **③ 远端 Agent** 的模型在电脑上，配置在 [连接 Agent](docs/CONNECT_AGENTS.md) 里完成，**不走资源库下载**。② 纯 API 同理，填地址与 Key 即可。

配置助手可一句话配。详细文档：

- [产品总览](docs/PRODUCT.md)
- [连接 Agent](docs/CONNECT_AGENTS.md)
- [本地模型](docs/LOCAL_MODEL.md)
- [端侧网关 + 本机工具](docs/REMOTE_BRAIN_LOCAL_TOOLS.md)
- [开发踩坑与已知 Bug](docs/PITFALLS.md)（协作者查坑首选）

④ Loop 要点（详见上链）：`LoopStep` 阶段上屏；`ToolRisk` 写确认 / 读剪贴板静默；满 8 步顶栏一键切已存 Hermes/WS（无则去添加）。

## 安全与隐私（必读）

- **`ws://` / `http://` 仅限同一 Wi‑Fi 局域网演示。** 公网或传密钥请用 **`wss://` / `https://`**。
- 本机**写操作**（日历/闹钟/开链/分享/写剪贴板/开应用/拨号/地图/邮件/写记忆等）**必须用户点确认**后才执行；读剪贴板与召回记忆可静默。闹钟优先系统时钟/倒计时；不支持则回退本机通知。
- **识图**走支持 vision 的通道（②/④ API、Hermes HTTP、③ Bridge）；本地小模型不看图。
- API Key 使用 `EncryptedSharedPreferences` 加密存储。

上手：[CONNECT_AGENTS.md](docs/CONNECT_AGENTS.md) · 验收：[ACCEPTANCE.md](docs/ACCEPTANCE.md)（含[最小真机矩阵](docs/ACCEPTANCE.md#最小真机矩阵扩用户前)） · 踩坑：[PITFALLS.md](docs/PITFALLS.md) · 安全：[SECURITY.md](docs/SECURITY.md)

## 技术架构（当前）

**用户只用做一件事：选档位。** 四档并列、顶栏一键切换；④ 只是其中一档的调度层，不是全 App 唯一中枢。

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

## 试用方式

> **不想自己编译？** 可直接下载 [Releases](https://github.com/yangwenhua212/hermchat/releases) 中的 APK 安装试用（同签名可覆盖升级）。

### 自己构建

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

以下**不是**当前预览版（0.1.33）能力，写在这里只为讲清方向。

### 订阅与恢复（远期 · 无账号）

> 坚决不做传统「注册登录」；也不用脆弱的设备指纹扛订阅恢复。

若日后有付费档 / 官方云能力，订阅状态与恢复手段优先：

| 方式 | 作用 |
|------|------|
| **商店购买凭证**（如 Google Play Billing） | 换机用同一 Google 账号恢复购买；云端以收据为准 |
| **本地 License / `.key` 文件** | 加密授权文件由用户备份到网盘；换机或重装后**自行导入** |

原则：

- **恢复责任在用户或商店账号**，不承诺「云端靠 device_id 自动找回订阅」。
- `device_id` / 安装实例 ID 若使用，仅作风控或统计弱信号，**不作订阅主键**。
- Android ID、自生成 UUID 会在卸载重装、部分刷机场景失效——不能当客服替代品。

**现状**：无账号、无订阅、无 License 文件；配置与 API Key 仅存本机。

### 通道超时降级

已有发送失败后的本轮 `AgentFailover`（③↔④，可落到 ②）；**不永久改当前 Agent**。长期切档靠顶栏手动切换。  
**首包超时（约 12s）**：④ 有本地模型则本轮改本地（顶栏「改用本地…」）；否则走 AgentFailover；不永久改当前 Agent。已吐字不降级。

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

**Q：为什么不用登录 / 注册账号？**  
A：HxSync 是**你自己的口袋客户端**：Agent 地址与 API Key 在本机，能力来自你选的 ①②③④，不是「先注册才能用的中心化 App」。上账号等于多一套身份系统、隐私面与客服预期，和当前「配置即连接」相反。若远期有付费，用**商店凭证**或**可备份的 License 文件**恢复权益，而不是邮箱密码账号体系。详见上文「订阅与恢复」。

**Q：服务商市场 / 订阅什么时候上线？**  
A：远期规划，无时间表。当前不依赖它们也能完整使用四档。

**Q：我能在 HxSync 上对外提供自己的 AI 服务吗？**  
A：目前只适合给自己或朋友**手动配置**。市场入驻属远期。App 是客户端，不是给你开公网入站网关；许可边界见 [COMMERCIAL.md](COMMERCIAL.md)。

**Q：会一直开源吗？**  
A：App 本体保持 **AGPL-3.0**（含版权方附加许可说明）。商业化若涉及官方云服务或闭源嵌入，走商业许可，不削弱开源客户端的核心能力。

## 文档索引

| 文档 | 说明 |
|------|------|
| [PRODUCT.md](docs/PRODUCT.md) | 产品与四档设计 |
| [CONNECT_AGENTS.md](docs/CONNECT_AGENTS.md) | 连接配置 |
| [LOCAL_MODEL.md](docs/LOCAL_MODEL.md) | 本地模型 / 资源库 |
| [REMOTE_BRAIN_LOCAL_TOOLS.md](docs/REMOTE_BRAIN_LOCAL_TOOLS.md) | 端侧网关 + 本机工具 |
| [ACCEPTANCE.md](docs/ACCEPTANCE.md) | 真机验收清单 / 日记 / **最小真机矩阵** |
| [PITFALLS.md](docs/PITFALLS.md) | 开发踩坑与已知 Bug（协作者首选） |
| [SECURITY.md](docs/SECURITY.md) | 安全 |
| [RELEASE.md](docs/RELEASE.md) | 签名与发版 |
| [ROADMAP.md](docs/ROADMAP.md) | 开发步骤 |
| [COMMERCIAL.md](COMMERCIAL.md) | 商用说明 |

## 许可

**[AGPL-3.0](LICENSE)** © HermChat Authors。分发 APK 时须提供对应源代码获取方式（App「关于」页已含仓库链接）。版权方附加许可与商业双轨见 [COMMERCIAL.md](COMMERCIAL.md)。
