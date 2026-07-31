# HxSync 0.1.8（开发预览）

**用途**：作者/协作者自行构建安装试用。未到 1.0，非商店上架包。

## 本版修复重点

- **长对话自动开新会话**：本地消息 ≥20 条后，下一次发送自动清空历史并强制换服务端 session（Hermes bridge），上下文归零、响应恢复秒回，根治「越聊越慢 → 流式断连」
- 新增 `startNewChat()` 公共接口（清历史 + 换 session），UI 入口后续版本接入

## 根因说明

Hermes bridge（WebSocket JSON-RPC）的 session 常驻服务端：App 侧不主动换 session 时，服务端上下文只增不减（实测 28 条消息 / 16K tokens 后明显变慢），context compressor 只能压 agent 内部上下文，管不了客户端复用旧会话的根因。本次在 App 侧加「阈值自动换会话」，换完上下文立刻归零。

## 安装

```bash
./gradlew :app:assembleRelease
```

若设备上仍是旧 debug 包：**先卸载再安装**。

---

## 历史版本

### 0.1.7（开发预览）

- **HTTP 测连**：按 OpenAI 兼容路径探测 `/v1/chat/completions`（失败再试 `/v1/models`），并带上 API Key / 模型名
- 测连失败时显示**具体原因**（Key 无效、超时、模型名等），不再只写「测连失败」
- API Key 粘贴时自动去掉误粘的前导括号等杂质

## 许可

AGPL-3.0 · 源代码：https://github.com/yangwenhua212/hermchat
