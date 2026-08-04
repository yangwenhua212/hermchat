# 能力现状 vs 下一步怎么实现

> 以本文件与 [ROADMAP.md](ROADMAP.md) 为准。

## 真实状态（代码已有）

| 能力 | 状态 | 入口 |
|------|------|------|
| 日历 / 闹钟工具 + 确认卡 | ✅ | 自然语言 → 确认卡 |
| 多 Agent / 扫码 / 快捷指令 / 主题 | ✅ | 顶栏 / 配置页 / 齿轮 |
| 系统语音唤醒 / ASR | ⚠️ 依赖机型 | 唤醒设置 → 引擎「系统」 |
| 离线唤醒 sherpa-onnx KWS | ✅ Step 12 | 唤醒设置 → 引擎「离线」→ 下载模型 → 开启监听 |
| 离线短指令 ASR | ✅ Step 14 | 唤醒后说指令；点按麦克风；自动发送进聊天 |
| 本地运行时 Phase B | ✅ Step 13 | 添加 Agent →「本地」；可选下载 Gemma；工具确认流可用 |
| Hermes HTTP 会话 / 新建对话 | ✅ Step 17 | 请求带 `X-Hermes-Session-Id`；顶栏新建对话；满 20 条自动换会话 |
| 气泡复制 / 编辑 Agent 跳转 | ✅ Step 17 | 长按选中复制；下拉菜单关闭后再导航 |
| ④ Agent loop 初版 | ✅ Step 18 | 确认后回灌 API 续跑；聪明路由；链接/搜索/分享 |
| Loop 中间态 / 分级确认 / 一键切 ③ | ✅ | 分析中/执行中/观察中；`ToolRisk`；超步数切 ③ |
| 剪贴板读写 | ✅ | `clipboard.read` 静默；`clipboard.write` 确认 |
| 开应用 / 拨号 / 地图 / 邮件 | ✅ | `app.open` / `phone.dial` / `maps.search` / `email.compose` |
| 本机极简记忆 | ✅ | `memory.recall` / `memory.remember`；见 [MEMORY.md](MEMORY.md) |
| 聊天识图（一期） | ✅ | Composer 选图；②/④/Hermes HTTP vision；③ Bridge attachment |
| 聊天附件（二期+三期） | ✅ | 文本/PDF；分享入；历史大图；Bridge 带图 |
| 首包超时降级 | ✅ | 约 12s → 本地或 AgentFailover |

---

## 聊天识图 Phase 1

- 输入条「图片」→ 相册；草稿缩略图可移除；气泡显示缩略图  
- OpenAI 兼容：`content` 多模态数组（text + `image_url` data URL）；图会压缩  
- ④ 有图强制走 API；① 顶栏短提示「不看图」；③ WS 可带 Bridge `attachment`  
- 二期再做：任意文件上 ③、系统分享入、历史回传大图 → **三期已做**

## 聊天附件 Phase 2

- Composer「附件」→ 系统文件选择（图 / PDF / txt / md / csv / json）  
- **文本**：截断注入 prompt（各档 Agent 可发，含本地/WS）  
- **PDF**：渲染首页为 JPEG，走 vision（同识图通道要求）  
- 气泡：真图缩略图；PDF/文本显示「PDF · 名」/「附件 · 名」

## 聊天附件 Phase 3

- **系统分享入**：`ACTION_SEND` 文本/图/PDF → 草稿或附件  
- **历史大图**：点气泡缩略图全屏查看；文件缺失提示「图片已失效」  
- **Bridge 真上传**：简易 WS / agent.message / JSON-RPC 带 `attachment`（base64）；见 [BRIDGE_PROTOCOL.md](BRIDGE_PROTOCOL.md)

### 后续可选（唤醒）

| 项 | 说明 |
|----|------|
| W4 | 多机型耗电 / 误唤醒调参 |

---

## 建议推进顺序

1. ~~第二工具（闹钟）~~ ✅  
2. ~~离线唤醒 sherpa KWS~~ ✅  
3. ~~本地运行时 Phase B~~ ✅ — 见 [LOCAL_MODEL.md](LOCAL_MODEL.md)  
4. ~~Hermes 会话 / 新建对话 / 复制~~ ✅ Step 17  
5. ~~④ Agent loop + 三关骨架~~ ✅ — 中间态 / `ToolRisk` / 超步数切 ③  
6. 真机验收「多步提醒 + 确认 + 续跑」；✅ JSON 纠正 / 本机记忆 / 首包超时 / 附件三期 / maps·email / Loop 阶段呈现；可选：界面深化、更多本机工具  

UI 文案规范：[UI.md](UI.md)。产品节奏：[ROADMAP.md](ROADMAP.md) v0.3.0。
