# HxSync 0.1.10（开发预览）

**用途**：作者/协作者自行构建安装试用。未到 1.0，非商店上架包。

## 本版重点

- **配置助手 · WebSocket**：可说「连电脑上的助手」局域网自动探测，或 `websocket 192.168.x.x` 扫主机常见端口；确认后测连保存
- **本地默认模型**：改为 Gemma 3 **270M** Q8（更轻、内存门槛更低）；仍可选 `gemma3-1b-it-int4`
- 延续 0.1.9：Hermes 少填主机、配置助手、HTTP Session、后台唤醒问云端等

## 安装

```bash
./gradlew :app:assembleRelease
```

若设备上仍是旧 **debug** 包：**先卸载再安装**。  
连续升级同一套 **release** 签名包（含 GitHub Releases）：**直接覆盖即可**。

---

## 历史版本

### 0.1.9（开发预览）

- **HTTP Hermes 会话**：`X-Hermes-Session-Id`；新建对话 / 切 Agent / 满 20 条换 UUID
- 配置类型 **Hermes**、**配置助手**（HTTP 一句话配）
- 聊天流式跟手、回前台续连、后台唤醒问云端
- 闹钟 queries + 倒计时 / 本机通知回退

### 0.1.8（开发预览）

- 长对话自动开新会话（本地 ≥20 条）；`startNewChat()` 接口

### 0.1.7（开发预览）

- HTTP 测连按 `/v1/chat/completions` 探测，失败显示具体原因

## 许可

AGPL-3.0 · 源代码：https://github.com/yangwenhua212/hermchat
