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
| Loop 中间态 / 分级确认 / 一键切 ③ | ✅ v0.3 三关骨架 | `LoopStep` · `ToolRisk` · `LoopEscalatePicker` |
| 剪贴板读写 | ✅ | `clipboard.read` 静默；`clipboard.write` 确认；见 [REMOTE_BRAIN_LOCAL_TOOLS.md](REMOTE_BRAIN_LOCAL_TOOLS.md) |
| 聊天识图（一期） | ✅ | Composer 选图；②/④/Hermes HTTP vision；本地/WS 短提示拒绝 |
| 聊天附件（二期） | ✅ | 附件：txt/md/json/csv 注入正文；PDF 首页当图；各通道文本可发 |

---

## 聊天识图 Phase 1

- 输入条「图片」→ 相册；草稿缩略图可移除；气泡显示缩略图  
- OpenAI 兼容：`content` 多模态数组（text + `image_url` data URL）；图会压缩  
- ④ 有图强制走 API；① / WS 顶栏短提示「不看图」  
- 二期再做：任意文件上 ③、系统分享入、历史回传大图

## 聊天附件 Phase 2

- Composer「附件」→ 系统文件选择（图 / PDF / txt / md / csv / json）  
- **文本**：截断注入 prompt（各档 Agent 可发，含本地/WS）  
- **PDF**：渲染首页为 JPEG，走 vision（同识图通道要求）  
- Bridge 仍无上传协议；真·远端落盘留给三期  
- 气泡：真图缩略图；PDF/文本显示「PDF · 名」/「附件 · 名」（PDF 仍用首页 JPEG 识图，界面不当相册图）

## Step 12 已落地（离线唤醒）

架构：

```
WakeWordService
    └── VoiceEngine
            ├── SpeechWakeEngine      ← 系统 SpeechRecognizer
            └── SherpaWakeEngine      ← sherpa-onnx KeywordSpotter
                    ↓
              VoiceEventBus → ChatScreen
```

- 设置可切换「系统 / 离线」；无系统引擎时默认倾向离线  
- 首次离线需下载 WenetSpeech KWS 模型（约数十 MB，写入 `filesDir`）  
- 命中唤醒词 → 震动 + 切入短指令 ASR → `Transcript(autoSend)` → 聊天执行  
- 点按麦克风同样走离线 ASR（无需系统语音引擎）

### 模型

| 用途 | 包 |
|------|----|
| 唤醒 KWS | WenetSpeech 3.3M（`KwsModelInstaller`） |
| 指令 ASR | zipformer zh-14M int8（`AsrModelInstaller`） |

### 后续可选

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
6. 真机验收「多步提醒 + 确认 + 续跑」与「剪贴板内容设提醒」；可选：首包超时降级、eraherm-memory、界面深化  

UI 文案规范：[UI.md](UI.md)。产品节奏：[ROADMAP.md](ROADMAP.md) v0.3.0。
