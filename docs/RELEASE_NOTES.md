# HxSync 0.1.7（开发预览）

**用途**：作者/协作者自行构建安装试用。未到 1.0，非商店上架包。

## 本版修复重点

- **HTTP 测连**：按 OpenAI 兼容路径探测 `/v1/chat/completions`（失败再试 `/v1/models`），并带上 API Key / 模型名
- 测连失败时显示**具体原因**（Key 无效、超时、模型名等），不再只写「测连失败」
- API Key 粘贴时自动去掉误粘的前导括号等杂质

## 安装

```bash
./gradlew :app:assembleRelease
```

若设备上仍是旧 debug 包：**先卸载再安装**。

## 许可

AGPL-3.0 · 源代码：https://github.com/yangwenhua212/hermchat
