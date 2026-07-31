# Release 构建与开源分发

当前公开发布版本：`versionName` **0.1.5** / `versionCode` **6**（见 `app/build.gradle.kts`）。

**给用户下载**：GitHub [Releases](https://github.com/yangwenhua212/hermchat/releases/latest) 上的 `HxSync-*.apk`（无需应用商店）。

## 快速构建（内测）

未配置正式签名时，Release 会**回退到 debug 签名**：

```bash
./gradlew :app:assembleRelease
```

APK：

```
app/build/outputs/apk/release/app-release.apk
```

建议复制为带版本名的文件再上传 Release：

```
dist/HxSync-0.1.5.apk
```

安装：

```bash
# Windows 示例
& "D:\Android\Sdk\platform-tools\adb.exe" install -r dist\HxSync-0.1.5.apk
```

无 adb 时：把 APK 拷到手机直接安装（允许未知来源）。

## 发布到 GitHub Release

```bash
gh release create v0.1.5 dist/HxSync-0.1.5.apk --title "HxSync 0.1.5" --notes-file docs/RELEASE_NOTES.md
```

## 正式签名（可选，以后有钱/有主体再配）

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
- [ ] README「下载安装」指向最新 Release
- [ ] APK 已挂到 GitHub Release
