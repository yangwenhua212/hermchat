# Release 构建与签名

当前版本：`versionName` **0.1.10** / `versionCode` **11**。

> **阶段**：开发预览。给自己试用 / 协作者测；**不是** 1.0 正式对外分发。

## 签名（强制）

Release **必须**使用长期有效的 `hermchat-release.jks`（约 10000 天），**禁止**再用 debug 签名发版。

1. 仓库根目录应有（均已 gitignore，**切勿提交**）：
   - `hermchat-release.jks`
   - `keystore.properties`

2. **立刻备份**上述两个文件到加密盘 / 密码管理器。丢失密钥 = 无法覆盖升级已装用户。

3. 构建：

```bash
./gradlew :app:assembleRelease
```

产物：`app/build/outputs/apk/release/app-release.apk`

也可发布到 GitHub Releases，供协作者下载同签名预览包。

## 升级安装（要不要先卸载）

| 情况 | 做法 |
|------|------|
| 已装包与新包均为同一 `hermchat-release.jks` 签名（含 GitHub 预览包互升） | **直接覆盖安装**，不必先删 |
| 已装包是 **debug** 签名，或曾换过密钥 / 证书不一致 | **先卸载**再装，否则系统拒绝覆盖 |
| 丢失 `hermchat-release.jks` 后重新生成密钥 | 与旧用户无法覆盖升级，只能卸载重装（数据丢失） |

从旧 debug 包升级到正式 release：**必须先卸载**。

## 若需重新生成密钥

```bash
keytool -genkeypair -v -storetype PKCS12 -keystore hermchat-release.jks \
  -alias hermchat -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=HxSync, OU=OpenSource, O=HermChat Authors, L=Internet, ST=NA, C=CN"
```

再写 `keystore.properties`：

```properties
storeFile=hermchat-release.jks
storePassword=你的密码
keyAlias=hermchat
keyPassword=你的密码
```

## 模型体积

Gemma 等本地权重**不打包进 APK**，首次在「本地」模式按需下载。APK 主要含运行时原生库。

## 安全提醒

局域网演示可用 `ws://`；**公网请用 `wss://` / `https://`，勿明文传密钥。** 详见 [SECURITY.md](SECURITY.md)。
