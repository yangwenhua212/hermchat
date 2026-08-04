# HxSync 0.1.28（开发预览）

**用途**：作者自用与协作者试用预览包。

## 本版重点

- **连接失败自动降级**（设置根目录开关，默认关）：③ 连接/发送失败时持久切到 ④>②>①；顶栏「已改用…」可切回
- 与本轮 `AgentFailover`（不改当前档）分离；可选 `fallbackAgentId`
- 文档同步：README / PRODUCT / REMOTE / ACCEPTANCE / NEXT_IMPL / ROADMAP / UI

延续 0.1.27：表述收口、maps/email、Loop 阶段文案。

## 安装

```bash
./gradlew :app:assembleRelease
```

同 release 签名可直接覆盖；debug → release 须先卸载。

---

## 历史版本

### 0.1.27

- 表述收口；maps.search / email.compose；Loop 分析中/执行中/观察中；相关修

### 0.1.26

- Loop 加固；app.open / phone.dial；本机记忆；首包超时；附件三期

### 0.1.25

- ④ 默认云端；「使用本地模型」须确认；手动列表藏 Hermes；PDF 按文件名展示

### 0.1.24

- 本地模型懒加载 + 分级内存门槛，修进聊天闪退
