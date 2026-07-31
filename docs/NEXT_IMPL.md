# 能力现状 vs 下一步怎么实现

> 以本文件与 [ROADMAP.md](ROADMAP.md) 为准。

## 真实状态（代码已有）

| 能力 | 状态 | 入口 |
|------|------|------|
| 日历 / 闹钟工具 + 确认卡 | ✅ | 自然语言 → 确认卡 |
| 多 Agent / 扫码 / 快捷指令 / 主题 | ✅ | 顶栏 / 配置页 / 齿轮 |
| 系统语音唤醒 / ASR | ⚠️ 依赖机型 | 唤醒设置 → 引擎「系统」 |
| 离线唤醒 sherpa-onnx KWS | ✅ Step 12 | 唤醒设置 → 引擎「离线」→ 下载模型 → 开启监听 |
| 离线短指令 ASR | ❌ 未做 | 唤醒后请键盘输入；后续可接 sherpa ASR |
| 本地运行时 Phase B | ❌ | [LOCAL_MODEL.md](LOCAL_MODEL.md) |

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
- 命中唤醒词 → 震动 +「在呢」；点按麦克风在离线模式下同样进入可输入状态（暂无离线 ASR）

### 后续可选

| 项 | 说明 |
|----|------|
| W3 | 唤醒后短指令离线 ASR |
| W4 | 多机型耗电 / 误唤醒调参 |

---

## 建议推进顺序

1. ~~第二工具（闹钟）~~ ✅  
2. ~~离线唤醒 sherpa KWS~~ ✅（ASR 可后置）  
3. 本地运行时 Phase B — 见 [LOCAL_MODEL.md](LOCAL_MODEL.md)  
4. 界面深化（头像、自定义快捷指令文案）  

UI 文案规范：[UI.md](UI.md)。
