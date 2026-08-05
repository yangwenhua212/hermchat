# HxSync 0.1.29（开发预览）

**用途**：作者自用与协作者试用预览包。

## 本版重点

- **移除**「连接失败自动降级」（③ 不再持久切档）
- **联网搜索**：`web.search` 回灌摘要；SearXNG→DuckDuckGo；可选博查/Tavily Key
- **打开官网**：已知域名直开；未知先搜再开（`url.open` 动作链）
- **拒违法**：Prompt + `LocalSafetyGuard` 高置信拦截并说明原因

## 安装

```bash
./gradlew :app:assembleRelease
```

同 release 签名可直接覆盖；debug → release 须先卸载。

---

## 历史版本

### 0.1.28

- 曾加入连接失败自动降级；随后从主线移除

### 0.1.27

- 表述收口；maps.search / email.compose；Loop 分析中/执行中/观察中；相关修

### 0.1.26

- Loop 加固；app.open / phone.dial；本机记忆；首包超时；附件三期

### 0.1.25

- ④ 默认云端；「使用本地模型」须确认；手动列表藏 Hermes；PDF 按文件名展示

### 0.1.24

- 本地模型懒加载 + 分级内存门槛，修进聊天闪退
