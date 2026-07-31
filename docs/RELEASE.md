# Release 构建

当前内测版本：`versionName` **0.1.1** / `versionCode` **2**（见 `app/build.gradle.kts`）。

产出可安装的 APK，供真机验收与分发。完整上手见仓库 [README.md](../README.md#五分钟上手真机--演示-bridge)。

## 快速构建（内测）

未配置正式签名时，Release 会**回退到 debug 签名**：

```bash
./gradlew :app:assembleRelease
```

APK：

```
app/build/outputs/apk/release/app-release.apk
```

安装：

```bash
# Windows 示例
& "D:\Android\Sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\release\app-release.apk
```

无 adb 时：把 APK 拷到手机直接安装（允许未知来源）。

Debug：`./gradlew :app:assembleDebug`。

## 正式签名（可选）

1. 生成密钥库（私钥勿提交）：

```bash
keytool -genkey -v -keystore hermchat-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias hermchat
```

2. 仓库根目录 `keystore.properties`（已 gitignore）：

```properties
storeFile=hermchat-release.jks
storePassword=你的密码
keyAlias=hermchat
keyPassword=你的密码
```

3. 再 `./gradlew :app:assembleRelease`。

## 发版检查

- [ ] `versionCode` 已递增
- [ ] 对照 [ACCEPTANCE.md](ACCEPTANCE.md) 跑通主路径
- [ ] README「五分钟上手」与当前端口/脚本一致
