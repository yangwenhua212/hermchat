# Release 构建

产出可安装的 APK，供真机验收与内测分发。

## 快速构建（内测）

未配置正式签名时，Release 会**回退到 debug 签名**，方便本地打包装机：

```bash
./gradlew :app:assembleRelease
```

APK 路径：

```
app/build/outputs/apk/release/app-release.apk
```

安装：

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

Debug 仍可用：`./gradlew :app:assembleDebug`。

## 正式签名（可选）

1. 生成密钥库（只做一次，私钥勿提交仓库）：

```bash
keytool -genkey -v -keystore hermchat-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias hermchat
```

2. 在仓库根目录创建 `keystore.properties`（已 gitignore）：

```properties
storeFile=hermchat-release.jks
storePassword=你的密码
keyAlias=hermchat
keyPassword=你的密码
```

`storeFile` 可为相对仓库根的路径，或绝对路径。

3. 再执行 `./gradlew :app:assembleRelease`，将使用正式签名。

## 版本号

在 `app/build.gradle.kts` 的 `defaultConfig` 中维护：

- `versionName`：对外版本，如 `0.1.0`
- `versionCode`：单调递增整数

发内测前建议对照 [ACCEPTANCE.md](ACCEPTANCE.md) 跑一遍。
