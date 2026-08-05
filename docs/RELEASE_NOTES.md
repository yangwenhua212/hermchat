# HxSync 0.1.33（开发预览）

**用途**：作者自用与协作者试用预览包。

## 本版重点

- **朗读中途不停**：QUEUE_ADD 句间不重抢音频焦点；Edge 回退系统按句追加；进设置不停播
- **打开抖音走 App**：有 App 别名且未提「官网」时不再误开网页；「打开抖音官网」仍开站

延续 0.1.32 基建收口。

## 安装

```bash
./gradlew :app:assembleRelease
```

同 release 签名可直接覆盖；debug → release 须先卸载。

---

## 历史版本

### 0.1.32

- 共享 OkHttp；Bridge close；模型下载离页继续

### 0.1.31

- 系统 TTS 加固；自动朗读只读一次

### 0.1.30

- 流式按句朗读；系统 TTS utteranceId 竞态初修
