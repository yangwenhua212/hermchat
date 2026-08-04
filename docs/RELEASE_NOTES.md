# HxSync 0.1.26（开发预览）

**用途**：作者自用与协作者试用预览包。

## 本版重点

- **④ Loop 加固**：闹钟话术加宽；tool JSON 坏格式自动纠正一轮；多步让云脑
- **新工具**：`app.open` / `phone.dial`（须确认）；本机极简记忆 `memory.recall` / `memory.remember`
- **首包超时**：约 12s 无回复 → 改本地或 AgentFailover
- **附件三期**：系统分享入；气泡大图；Bridge `attachment` 带图
- 文档：`MEMORY.md`、Bridge 协议补充

延续 0.1.25：网关默认云端、藏 Hermes 入口、PDF 按文件名展示。

## 安装

```bash
./gradlew :app:assembleRelease
```

同 release 签名可直接覆盖；debug → release 须先卸载。

---

## 历史版本

### 0.1.25

- ④ 默认云端；「使用本地模型」须确认；手动列表藏 Hermes；PDF 按文件名展示

### 0.1.24

- 本地模型懒加载 + 分级内存门槛，修进聊天闪退

### 0.1.23

- 识图 / 附件；配置助手四档引导

### 0.1.22

- 聊天壁纸仅铺消息列表

### 0.1.21

- 实验「本地优先解析」
