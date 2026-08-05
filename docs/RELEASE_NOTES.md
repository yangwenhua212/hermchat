# HxSync 0.1.31（开发预览）

**用途**：作者自用与协作者试用预览包。

## 本版重点

- **系统 TTS 加固**：绑定系统首选引擎；`STREAM` 参数修正；忽略过期 `onStop`，避免无声；设置页可「试听当前引擎」
- **自动朗读只读一次**：只读 AI 本轮刚生成的回复；离开再进聊天不会重读（见 `.cursor/rules/auto-speak.mdc`）

延续 0.1.30：流式按句朗读。

## 安装

```bash
./gradlew :app:assembleRelease
```

同 release 签名可直接覆盖；debug → release 须先卸载。

---

## 历史版本

### 0.1.30

- 流式按句朗读；系统 TTS utteranceId 竞态初修

### 0.1.29

- 移除③持久自动降级；联网搜索回灌；打开官网链；拒违法

### 0.1.28

- 曾加入连接失败自动降级；随后从主线移除

### 0.1.27

- 表述收口；maps.search / email.compose；Loop 阶段文案
