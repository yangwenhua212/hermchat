# HxSync 0.1.11（开发预览）

**用途**：作者自用与协作者试用预览包。

## 本版重点

- **④ 端侧网关**：本地 Gemma + DeepSeek 等 API 混合路由；本机闹钟/日历；气泡标 `网关·本地` / `网关·API`
- **④↔③ 自动故障转移**：主通道失败时本轮自动改用已保存的远端 Agent / 网关（不永久切换）
- 添加 Agent 不再闪回；下载显示大小/进度/速度；唤醒「停止听」与真实准备状态
- 文档四档模型；ACCEPTANCE 改自用日记；去掉上架口吻

## 安装

```bash
./gradlew :app:assembleRelease
```

同 release 签名可直接覆盖；debug → release 须先卸载。

---

## 历史版本

### 0.1.10

- WebSocket 配置助手局域网探测；本地默认 Gemma 3 270M

### 0.1.9

- Hermes Session、配置助手、后台唤醒问云端等

## 许可

AGPL-3.0 · https://github.com/yangwenhua212/hermchat
