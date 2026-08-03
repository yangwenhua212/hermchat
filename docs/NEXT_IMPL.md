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
| Loop 中间态 / 分级确认 / 一键切 ③ | ✅ v0.3 三关骨架 | `LoopStep` · `ToolRisk` · `LoopEscalatePicker`；见 [REMOTE_BRAIN_LOCAL_TOOLS.md](REMOTE_BRAIN_LOCAL_TOOLS.md) |

---

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
6. 真机验收「多步提醒 + 确认 + 续跑」；可选：首包超时降级、READ_ONLY 读工具、界面深化  

UI 文案规范：[UI.md](UI.md)。产品节奏：[ROADMAP.md](ROADMAP.md) v0.3.0。
