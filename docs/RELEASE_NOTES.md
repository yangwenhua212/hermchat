# HxSync 0.1.9（开发预览）

**用途**：作者/协作者自行构建安装试用。未到 1.0，非商店上架包。

## 本版修复重点

- **HTTP Hermes 会话**：每次请求带 `X-Hermes-Session-Id`；「新建对话」/切 Agent/满 20 条自动换新 UUID，避免隐式会话把上下文堆到上万 tokens
- 配置类型 **Hermes**：只填主机 + API Key，自动拼 `http://…`
- **配置助手**：添加 Agent 时可用对话一句话自动识别主机/Key、测连并保存（解析后先确认再连）
- 聊天页更跟手：流式按 token 直出、贴底跟随、等待三点动画、气泡约 78% 宽
- 进后台短暂不断：回前台自动续连（HTTP 保留 Session；WS 重连且尽量不换会话）
- **后台唤醒 → 自动问云端**：唤醒识别到指令后发给当前 Agent；无聊天页时通知栏展示回复
- 聊天顶栏增加「新建对话」；气泡文字可长按选中复制
- 修复下拉菜单点「编辑/添加 Agent」有时不跳转（关闭菜单后再导航）
- 闹钟：补 Android 11+ queries；倒计时 / 本机通知回退

## 安装

```bash
./gradlew :app:assembleRelease
```

若设备上仍是旧 **debug** 包：**先卸载再安装**。  
连续升级同一套 **release** 签名包（含 GitHub Releases）：**直接覆盖即可**。

---

## 历史版本

### 0.1.8（开发预览）

- 长对话自动开新会话（本地 ≥20 条）；`startNewChat()` 接口

### 0.1.7（开发预览）

- HTTP 测连按 `/v1/chat/completions` 探测，失败显示具体原因

## 许可

AGPL-3.0 · 源代码：https://github.com/yangwenhua212/hermchat
