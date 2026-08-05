# 开发踩坑与已知 Bug（HxSync）

开发过程中踩过的坑、真机回归、易再犯的设计陷阱。**给作者与协作者查**，不是用户手册。

- 验收清单与真机日记仍在 [ACCEPTANCE.md](ACCEPTANCE.md)
- 专题细节（连接 / 工具 / 本地模型）仍写在对应文档；这里只保留「现象 → 原因 → 处理」索引
- **修完同类 bug 后**：同一轮往本表追加一行，并在 `ACCEPTANCE` 日记补一句

维护约定见 `.cursor/rules/docs-sync.mdc`。

---

## 怎么用

1. 按**现象**搜（Ctrl+F），或按下面分类扫。
2. 「处理」里的版本号表示**大致从哪版修起**；若你装的更旧，对照升级。
3. 新坑优先记这里；若只属于某一专题，可在专题文档留一句并链回本页。

---

## 连接 / Agent / ④ Loop

| 现象 | 原因 | 处理 |
|------|------|------|
| 添加 Agent 闪回聊天 | ViewModel 残留 `completedProfile` | sessionKey + 进页重置 |
| `10.0.2.2` 真机不通 | 仅模拟器映射本机 | 改电脑局域网 IP |
| HTTP 多轮失忆 | 未用 Hermes Session 且旧版不带历史 | HTTP 兼容带短历史；Hermes 靠 Session-Id |
| 一退出 App 连接就断 | 连接绑在 Activity ViewModel，`onCleared` 关 socket | Application 会话 + 保活通知 |
| 云端一直转圈无字 | 首包无限等 | 12s 无首包 → ④ 改本地或 AgentFailover |
| HTTP 断线后回复重复一截 | 吐字后仍重试整段流 | 已吐字不重试 |
| 网关下了模型仍不走本地 | 旧版写死体积 / API 名当权重 id | `localModelId` + 资源库选用 |
| 网关气泡先弱文案再叠一段 API | 自动 escalate 时本地已 emit | 取消闲聊自动走本地；默认云端 |
| 闲聊误走本地 / 路由难懂 | AUTO 寒暄分流 | 默认云端；「使用本地模型」风险确认 |
| ④ 像纯 API 无 Agent | 单轮 chat、工具结果不回灌 | 确认后回灌续跑（loop） |
| ④ Loop 黑盒像死机 | 无阶段反馈 | `LoopStep` 中间态（分析/执行/观察） |
| 确认后 Loop 与 UI 脱节 | 确认在独立协程、取消易留半截 | 写工具 `suspend` 等待确认；取消清 loop |
| 远端 payload 未确认就执行 | `needConfirm` 展示前已被置 true | 解析默认未授权；仅确认后 / READ_ONLY 可执行 |
| ④ 超步数只能干瞪眼 | 仅错误文案 | 一键切已存 ③；无则去添加 |
| tool JSON 吐错就断 | 无重试 | 坏格式/假装已执行 → 自动纠正一轮 |
| 顶栏英文 `Unable to resolve host` | 直接展示异常 message | `UserFacingError` 中文短句 |

详见：[CONNECT_AGENTS.md](CONNECT_AGENTS.md)、[REMOTE_BRAIN_LOCAL_TOOLS.md](REMOTE_BRAIN_LOCAL_TOOLS.md)、[BRIDGE_PROTOCOL.md](BRIDGE_PROTOCOL.md)

---

## 本机工具 / Planner

| 现象 | 原因 | 处理 |
|------|------|------|
| ④ 闹钟 Loop 用不了 | 话术未命中 / 秒级戳 / 只开时钟 UI / 多步被本地抢 | 加宽 planner；秒→毫秒；SKIP_UI；多步让云脑 |
| 确认闹钟显示成功但不响 | 无通知/精确闹钟权限却假成功 | 缺权限明确失败并引导设置 |
| 通知栏有提醒但系统闹钟没有 | 时钟唤起失败后静默回退通知 | 优先 SET_ALARM/厂商包；回退文案标明 |
| 「打开抖音」却开网页 | `LocalUrlOpenPlanner` KNOWN_SITES 先于 `app.open` | 有 App 别名且无「官网」词时让路 `app.open` |
| 读剪贴板也要确认卡 | 无 READ_ONLY 工具 | `clipboard.read` 静默；`write` 仍确认 |
| 想用本地试解析工具却无入口 | 旧政策「① 永不驱动 Loop」 | 设置「本地优先解析」+ 开前警告 |

详见：[REMOTE_BRAIN_LOCAL_TOOLS.md](REMOTE_BRAIN_LOCAL_TOOLS.md)

---

## 朗读 / TTS

| 现象 | 原因 | 处理 |
|------|------|------|
| 点喇叭没声音 | 中文包 / utterance 竞态 / 音频属性 | 绑首选引擎；STREAM 用 String；忽略过期 onStop |
| 系统设置能播、App 无声 | 绑错引擎 / `putInt(STREAM)` / stop 竞态 | ≥0.1.31 加固；设置页「试听」 |
| 自动朗读离开再进又读一遍 | `remember` 的 lastSpoken 离页丢失 | Application 级 `autoHandled` + 进页 prime（见 `.cursor/rules/auto-speak.mdc`） |
| 自动朗读读到一半停 | QUEUE_ADD 句间 abandon 焦点；Edge 回退每句 flush；离页 `DisposableEffect.stop` | 句间不重抢焦点；回退 QUEUE_ADD；卸聊天页不停播 |
| 已改系统朗读仍显示云端 404 | `lastError` 粘住 | 开读清错误；自动静默回退；错误 SharedFlow |
| Hermes 已配 Edge，手机云端仍 404 | 对着 Hermes 聊天地址打 `/v1/audio/speech` | 选「Edge 小艺」直连微软 |

详见：[CONNECT_AGENTS.md](CONNECT_AGENTS.md)、[UI.md](UI.md)

---

## 本地模型 / 资源库

| 现象 | 原因 | 处理 |
|------|------|------|
| 本地模型下载 HTTP 401 | Gemma 门控、未填 HF 令牌 | 默认 Qwen/TinyLlama 免令牌；Gemma 仍要令牌 |
| 下载中断重来 / 不能暂停 | 无 Range、删 `.part` | 暂停保留断点，可继续 |
| 离开资源库页下载被取消 | Job 挂在 Compose `rememberCoroutineScope` | 下载挂 `LocalModelStore`（离页不取消） |
| 下载 TinyLlama/Qwen 后进聊天闪退 | 进聊天预加载 MediaPipe OOM | 懒加载；分级内存门槛；`largeHeap` |
| TinyLlama 中文乱码 | 模型偏英文 | 目录标明；中文用 Qwen2.5 |
| 对 TinyLlama 说英文却回中文 | App 写死简体中文 system | TinyLlama 改跟用户语言 |

详见：[LOCAL_MODEL.md](LOCAL_MODEL.md)

---

## UI / 会话 / 配置

| 现象 | 原因 | 处理 |
|------|------|------|
| 签名冲突无法覆盖安装 | debug / release 证书不同 | 先卸载再装（见 [RELEASE.md](RELEASE.md)） |
| 发送后输入框转圈不能打字 | 忙碌态锁死 Composer | 思考在机器人气泡，输入框可打字 |
| 顶栏挂一整段回复 | 语音 Status 塞摘要且不消失 | 短状态 + 单行 + 自动消失 |
| 流式末尾一根死杠 `\|` | 文本拼接光标 | 思考转圈、吐字键盘图标 |
| 新建对话后旧聊找不到 | 旧版整表清空 messages | 多会话 Room；历史列表 |
| 历史一股脑全 Agent / 不能删 | 未按 Agent 过滤；无删除入口 | 按当前 Agent 过滤；项旁删除 |
| 配置助手不认裸 Key | 只认 sk-/带标签 | 整段粘贴密钥可识别 |
| 手动配置 Hermes 与 HTTP 重复 | 类型列表多一项 | 手动列表隐藏 Hermes |
| 主题只改气泡不改背景 | Atmosphere 未接 theme | 主题渐变 + 可图片壁纸 |
| 系统返回直接回桌面 | 未接 BackHandler | 与页内「返回」同路径 |
| 传 PDF 却像发图片 | PDF 渲成 JPEG 后按 IMAGE 展示 | 按「PDF · 文件名」展示 |
| 通知一直「正在听」却无反应 | 模型还在下 / 引擎未起 | 看进度；停止听；关后台监听 |

详见：[UI.md](UI.md)、[PRODUCT.md](PRODUCT.md)

---

## 基建（开发者注意）

| 现象 / 风险 | 原因 | 处理 |
|------|------|------|
| 多处各自 `OkHttpClient` | Cursor 冗余 | `SharedHttpClients` 共享连接池 |
| Bridge `close` 后仍可能有重连 Job | 只 cancel `reconnectJob` | `close` 取消根 `SupervisorJob`（实例勿复用） |
| VoiceCloudBridge 与前台会话双连接 | 后台独立 `AIClientFactory.create` | 尚未合并；改连接逻辑时两边都要测 |

---

## 追加模板

新坑请复制：

```markdown
| 简短现象 | 根因（文件/机制） | ≥版本或修复要点；相关文档链接 |
```

分类放错没关系，先记下再挪。
