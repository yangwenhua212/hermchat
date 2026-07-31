# HxSync 0.1.6（开发预览）

**用途**：作者/协作者自行构建安装试用。未到 1.0，非商店上架包。

## 本版修复重点

- **正式 release 签名**（长期证书；勿用 debug 签名发版）
- 本地大模型：按需下载 + **内存不足拒绝加载**
- App 内「关于」：AGPL-3.0 与源代码链接
- 本机工具强制确认；文档强调 `ws://` 仅限局域网

## 安装

```bash
./gradlew :app:assembleRelease
```

若设备上仍是旧 debug 包：**先卸载再安装**。

## 许可

AGPL-3.0 · 源代码：https://github.com/yangwenhua212/hermchat
