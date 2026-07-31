# 能力现状 vs 下一步怎么实现

> 若你看到「多 Agent / 扫码 / 快捷指令尚未开发」一类表述，那是旧规划稿；以本文件与 [ROADMAP.md](ROADMAP.md) 为准。

## 真实状态（代码已有）

| 能力 | 状态 | 入口 |
|------|------|------|
| 日历工具 + 确认卡 | ✅ | 说「明天下午3点提醒我开会」 |
| 多 Agent 切换 / 添加 / 编辑 | ✅ | 顶栏名字下拉 |
| 扫码 / 粘贴配置 | ✅ | 配置页「扫码导入」（竖屏） |
| 快捷指令 + 输入偏好 | ✅ | 聊天底栏 + 右上角齿轮 |
| 气泡主题色 / 圆角 | ✅ | 齿轮 → 聊天主题色 / 气泡样式 |
| 系统语音唤醒 / ASR | ⚠️ 依赖机型 | 无系统语音引擎则不可用（你当前手机即是） |
| 离线唤醒 sherpa / Vosk | ❌ 未集成 | 见下文实现路径 |
| 第二个本机工具（如闹钟/提醒） | ❌ 未做 | 见下文；日历是第一个 |

短信工具产品上刻意后置（权限与合规重），优先扩展 **闹钟/本地提醒**。

---

## 离线唤醒如何实现（sherpa-onnx 推荐）

目标：不依赖系统 `SpeechRecognizer`，前台服务里本地听唤醒词 + 短指令。

### 架构（已预留）

```
WakeWordService
    └── VoiceEngine（接口）
            ├── SpeechWakeEngine      ← 现有：系统识别
            └── SherpaWakeEngine      ← 新增：sherpa-onnx
                    ↓
              VoiceEventBus（WakeDetected / Transcript / Error）
                    ↓
              ChatScreen 收事件 → 填输入框 / 自动发送
```

换引擎时 **不必改 UI**：只换 `WakeWordService.ensureEngine()` 里创建的实现。

### 推荐方案：sherpa-onnx（关键词检测 + 可选 ASR）

1. **依赖**  
   - 引入官方 Android AAR / JNI（`sherpa-onnx`）  
   - 或把 `.so` + Java/Kotlin 绑定打进 `app/src/main/jniLibs`

2. **模型资源（放 assets，首次拷到 filesDir）**  
   - 唤醒：关键词模型（可定制「小助手」「HxSync」等）  
   - 指令（可选）：流式 ASR 小模型（中文）  
   - 体积需控制（目标：唤醒模型尽量小；ASR 可后置，先只做唤醒成功后弹输入法）

3. **实现 `SherpaWakeEngine`**  
   - `AudioRecord` 持续读 PCM  
   - 喂给 Keyword Spotting → 命中则 `VoiceEventBus.emit(WakeDetected)`  
   - 可选：命中后切一段 ASR → `Transcript(text, autoSend)`  
   - 注意：前台服务 + `FOREGROUND_SERVICE_MICROPHONE`（已有）

4. **设置页**  
   - 「引擎：系统 / 离线 sherpa」  
   - 无系统引擎时默认选离线；系统不可用则禁止开「系统」开关

5. **验收**  
   - 飞行模式下喊唤醒词仍有反馈  
   - 杀后台后通知栏可停；国产 ROM 仍建议白名单

### 备选：Vosk

- 集成更简单、模型成熟，但实时 KWS 体验通常弱于 sherpa 关键词方案  
- 适合「点一下再说」；持续唤醒更推荐 sherpa

### 工作量粗估

| 阶段 | 内容 | 约 |
|------|------|-----|
| W1 | VoiceEngine 接口抽出 + 设置切换 | 0.5–1 天 |
| W2 | sherpa AAR + 关键词模型跑通唤醒 | 2–3 天 |
| W3 | 唤醒后短指令 ASR + 耗电/稳定性 | 2 天 |
| W4 | 真机多机型验收 | 1 天 |

---

## 第二个本机工具如何实现（建议：闹钟 / 提醒）

复用日历同一套模式，不要另起炉灶。

1. **`AlarmTool : PhoneTool`**  
   - `name = "alarm.create"`  
   - 权限：`SCHEDULE_EXACT_ALARM`（Android 12+）/ `POST_NOTIFICATIONS`  
   - 用 `AlarmManager` 或写入系统闹钟（`AlarmClock.ACTION_SET_ALARM` Intent，兼容性更好）

2. **`LocalAlarmPlanner`**  
   - 解析「半小时后提醒我喝水」「明天早上8点叫我」  
   - 产出 `ToolCall(needConfirm = true)`

3. **`ToolRegistry` 注册**；`ToolCallParser` 增加摘要文案

4. **确认卡** 已有 → 允许后执行 → 系统消息「已设提醒」

5. **快捷指令** 可加「提醒我…」

验收：确认前不响铃；允许后到点提醒；拒绝无副作用。

---

## 建议推进顺序

1. **第二工具（闹钟）** — 快、立刻强化「确认后动手」  
2. **离线唤醒 sherpa** — 解决你手机无系统语音引擎的硬伤  
3. 界面再深化（头像、自定义快捷指令文案编辑）— 主题/指令主干已有  

若要开工，直接说「做闹钟」或「做 sherpa」，按上面路径落地即可。
