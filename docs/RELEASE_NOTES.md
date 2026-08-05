# HxSync 0.1.32（开发预览）

**用途**：作者自用与协作者试用预览包。

## 本版重点

- **基建收口**：进程级共享 OkHttp（连接池复用）；Bridge `close` 取消根协程 Job
- **模型下载**：挂在 `LocalModelStore`，离开资源库页不中断；暂停仍有效
- **VoiceCloudBridge / ReplySpeaker**：collect / turn / stop 路径可取消，减少残留 Job

延续 0.1.31：系统 TTS 加固、自动朗读只读本轮一次。

## 安装

```bash
./gradlew :app:assembleRelease
```

同 release 签名可直接覆盖；debug → release 须先卸载。

---

## 历史版本

### 0.1.31

- 系统 TTS 加固；自动朗读只读一次

### 0.1.30

- 流式按句朗读；系统 TTS utteranceId 竞态初修

### 0.1.29

- 移除③持久自动降级；联网搜索回灌；打开官网链；拒违法

### 0.1.28

- 曾加入连接失败自动降级；随后从主线移除

### 0.1.27

- 表述收口；maps.search / email.compose；Loop 阶段文案
