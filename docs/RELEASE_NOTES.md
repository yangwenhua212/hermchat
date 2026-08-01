# HxSync 0.1.12（开发预览）

**用途**：作者自用与协作者试用预览包。

## 本版重点

- **朗读回复**：系统 TTS；助手气泡喇叭可点读/停；设置里可开自动朗读
- **设置树状目录**：默认输入 / 外观 / 快捷指令点进再看详情，根页更短
- 关于页去掉重复的 HxSync 标题

延续 0.1.11：端侧网关、④↔③ 故障转移、配置助手等。

## 安装

```bash
./gradlew :app:assembleRelease
```

同 release 签名可直接覆盖；debug → release 须先卸载。

---

## 历史版本

### 0.1.11

- ④ 端侧网关混合路由；④↔③ 自动故障转移
- 添加 Agent 闪回修复；下载进度；唤醒停止听

### 0.1.10

- WebSocket 配置助手探测；本地默认 Gemma 270M

## 许可

AGPL-3.0 · https://github.com/yangwenhua212/hermchat
