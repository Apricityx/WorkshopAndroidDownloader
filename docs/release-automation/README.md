# Release Automation Guide

本目录说明当前仓库使用的 GitHub Actions 发布流程。

## 范围

- 在 GitHub Actions 中构建已签名的 Android `release APK`
- 当推送 `v*` 标签时，自动发布到 GitHub Releases
- 手动触发 `workflow_dispatch` 时，仅构建并上传 Actions artifact，不自动创建 Release

工作流文件：
- `.github/workflows/release.yml`

## 1. 准备签名密钥

生成 upload keystore：

```powershell
keytool -genkeypair -v `
  -keystore .\signing\workshop-release.jks `
  -alias upload `
  -keyalg RSA -keysize 2048 -validity 10000
```

把 keystore 转成 Base64，供 GitHub Secret 使用：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes(".\signing\workshop-release.jks")) `
  | Set-Content -NoNewline .\signing\keystore.base64.txt
```

说明：
- 不要把 `.jks` 或 `keystore.base64.txt` 提交到仓库。
- 如果 keystore 是 `PKCS12`，通常 `RELEASE_KEY_PASSWORD` 和 `RELEASE_STORE_PASSWORD` 相同。

## 2. 配置 GitHub Environment

工作流使用的 Environment 名称：
- `release-signing`

推荐在 GitHub 仓库里创建这个 Environment，然后把签名信息放到它的 Secrets。

### Environment Secrets

- `ANDROID_KEYSTORE_BASE64`
  - `workshop-release.jks` 的 Base64 内容
- `RELEASE_STORE_PASSWORD`
  - keystore 密码
- `RELEASE_KEY_ALIAS`
  - key alias，例如 `upload`
- `RELEASE_KEY_PASSWORD`
  - key 密码

当前这套流程不依赖额外的 GitHub Environment Variables；只需要上面 4 个 Secrets。

## 3. 版本号配置

版本号已经外置到根目录 `gradle.properties`：

```properties
application.version.code=1
application.version.name=1.0
```

发布前先更新这两个值，再提交到仓库。

建议约定：
- Git 标签：`v<application.version.name>`
- 发布说明文件：`docs/release/note/v<application.version.name>.md`

例如：
- 版本号：`1.2.0`
- Git 标签：`v1.2.0`
- 发布说明文件：`docs/release/note/v1.2.0.md`

## 4. 触发发布

### 推荐流程

1. 修改 `gradle.properties` 中的 `application.version.code` 和 `application.version.name`
2. 新建或更新 `docs/release/note/v<version>.md`
3. 提交变更
4. 创建并推送标签 `v<version>`

PowerShell 示例：

```powershell
git add gradle.properties docs/release/note/v1.0.md
git commit -m "chore(release): prepare v1.0"
git tag -a v1.0 -m "Release v1.0"
git push origin HEAD
git push origin v1.0
```

### 工作流行为

- `push` 到 `v*` 标签：
  - 运行 lint
  - 构建签名版 `release APK`
  - 上传 Actions artifact
  - 创建或更新对应的 GitHub Release
  - 如果存在 `docs/release/note/v<version>.md`，则使用它作为 Release 正文

- `workflow_dispatch`：
  - 运行 lint
  - 构建签名版 `release APK`
  - 上传 Actions artifact
  - 不自动发布 GitHub Release

## 5. 本地构建签名版 APK

如果你要在本地验证签名构建，可先设置环境变量：

```powershell
$env:RELEASE_STORE_FILE="C:\path\to\workshop-release.jks"
$env:RELEASE_STORE_PASSWORD="..."
$env:RELEASE_KEY_ALIAS="upload"
$env:RELEASE_KEY_PASSWORD="..."
.\gradlew.bat :app:assembleRelease
```

输出目录：
- `app/build/outputs/apk/release/`

## 6. 常见问题

`Missing release signing env vars`
- 原因：本地或 CI 没有提供完整的 4 个签名环境变量
- 修复：检查 `RELEASE_STORE_FILE`、`RELEASE_STORE_PASSWORD`、`RELEASE_KEY_ALIAS`、`RELEASE_KEY_PASSWORD`

`RELEASE_STORE_FILE does not exist`
- 原因：工作流解码失败，或本地变量指向了错误路径
- 修复：重新生成 `ANDROID_KEYSTORE_BASE64`，并确认 keystore 文件有效

`KeytoolException` / `BadPaddingException`
- 原因：keystore、alias、密码不匹配
- 修复：重新核对 `ANDROID_KEYSTORE_BASE64`、`RELEASE_STORE_PASSWORD`、`RELEASE_KEY_ALIAS`、`RELEASE_KEY_PASSWORD`

`Could not determine java version` 或找不到本地 JDK
- 原因：机器上没有可用 JDK，或仓库里误写了本机专用的 `org.gradle.java.home`
- 修复：使用系统 `JAVA_HOME` / Android Studio JDK；当前工作流会显式使用 `setup-java` 提供的 JDK 21
