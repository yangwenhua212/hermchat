# HxSync 0.1.30（开发预览）

**用途**：作者自用与协作者试用预览包。

## 本版重点

- **流式按句朗读**：开自动朗读后边出字边读（系统 TTS 用队列追加，延迟更低）
- **修系统 TTS 无声**：utteranceId 不再复用 messageId，避免 stop 回调掐声；Edge 失败回退系统

延续 0.1.29：联网搜索、打开官网链、拒违法。

## 安装

```bash
./gradlew :app:assembleRelease
```

同 release 签名可直接覆盖；debug → release 须先卸载。

---

## 历史版本

### 0.1.29

- 移除③持久自动降级；联网搜索回灌；打开官网链；拒违法

### 0.1.28

- 曾加入连接失败自动降级；随后从主线移除

### 0.1.27

- 表述收口；maps.search / email.compose；Loop 阶段文案
