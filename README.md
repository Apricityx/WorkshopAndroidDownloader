<p align="center">
  <img src="./app/src/main/res/mipmap-nodpi/ic_launcher.png" alt="WorkshopOnAndroid 图标" width="96" height="96" />
</p>

<h1 align="center">WorkshopOnAndroid</h1>

<p align="center">
  一个面向 Android 的 Steam 创意工坊公开模组下载器项目
</p>

<p align="center">
  支持浏览支持创意工坊的游戏、查看模组详情、下载公开条目，并自动导出到系统下载目录
</p>

<p align="center">
  <a href="#quick-start">
    <img alt="Quick Start" src="https://img.shields.io/badge/Build-Quick%20Start-6f42c1?style=for-the-badge&logo=gradle&logoColor=white" />
  </a>
  <a href="#automation">
    <img alt="ADB Automation" src="https://img.shields.io/badge/ADB-Debug%20Automation-0969da?style=for-the-badge&logo=androidstudio&logoColor=white" />
  </a>
</p>

<p align="center">
  <img alt="Android API 31+" src="https://img.shields.io/badge/Android-API%2031%2B-34A853?style=flat-square&logo=android&logoColor=white" />
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-Android-7f52ff?style=flat-square&logo=kotlin&logoColor=white" />
  <img alt="Jetpack Compose" src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white" />
  <img alt="Steam Workshop Public Items" src="https://img.shields.io/badge/Steam%20Workshop-Public%20Items-1b2838?style=flat-square&logo=steam&logoColor=white" />
  <img alt="Anonymous Steam CM" src="https://img.shields.io/badge/Protocol-Anonymous%20Steam%20CM-0a7ea4?style=flat-square" />
</p>

<p align="center">
  <a href="#highlights">核心特性</a> •
  <a href="#quick-start">快速开始</a> •
  <a href="#build-from-source">构建说明</a> •
  <a href="#release-automation">发布自动化</a> •
  <a href="#automation">自动化调试</a> •
  <a href="#repository-layout">仓库结构</a> •
  <a href="#limitations">当前限制</a>
</p>

> [!IMPORTANT]
> 当前实现只面向 **公开可访问** 的 Steam Workshop 条目，不需要 Steam 登录，但也不支持私有内容、好友可见内容和 `Collection` 下载。

<a id="highlights"></a>

## 核心特性

| 方向    | 说明                                                                    |
|-------|-----------------------------------------------------------------------|
| 游戏发现  | 支持预置热门工坊游戏、搜索 Steam 游戏、或直接输入 `AppID` 加入本地游戏库。                         |
| 工坊浏览  | 基于 Steam Community 页面抓取创意工坊列表，支持搜索、分页和模组详情查看。                         |
| 调试自动化 | 提供基于 `adb` 的 Gradle 任务，可跳过 UI 直接触发下载并拉回日志。                            |

> [!NOTE]
> `:steam-protocol` 负责 Steam CM 匿名连接、内容服务器发现和 CDN 授权，`:workshop-core` 负责 manifest、chunk、校验、解压和最终文件组装。

<a id="quick-start"></a>

## 快速开始

### 1. 准备环境

- JDK 17+，当前项目使用 JDK 21 构建
- Android Studio / Android SDK
- Android 12+ 设备
- 能访问 Steam Store、Steam Community 和 Steam Web API 的网络环境
- 如需自动化调试，请确保 `adb` 可用且设备已连接

### 2. 构建 Debug APK

```powershell
.\gradlew.bat :app:assembleDebug
```

### 3. 安装到设备

```powershell
.\gradlew.bat :app:installDebug
```

### 4. 应用内使用

1. 启动应用后进入游戏库页面。
2. 点击右上角 `+`，打开添加游戏页。
3. 通过热门游戏、关键字搜索或直接输入 `AppID` 把支持创意工坊的游戏加入库。
4. 进入游戏对应的创意工坊页，搜索并查看模组详情。
5. 点击模组卡片上的 `下载`，将任务加入下载中心。
6. 在下载中心查看状态、日志、导出文件，或执行暂停、继续、删除操作。

### 5. 下载结果位置

- 应用私有缓存目录：`/data/user/0/top.apricityx.workshop/files/workshop/<AppID>/<PublishedFileId>/`
- 用户可访问导出目录：`/sdcard/Download/workshop/<AppID>/<PublishedFileId>/`

如果模组是单文件，并且 Steam 元数据里带有更可读的标题或文件名，导出阶段会优先使用它们生成文件名。

<a id="build-from-source"></a>

## 构建说明

| 模块 | 职责 |
| --- | --- |
| `:app` | Compose UI、页面流转、游戏库、本地设置、下载中心和文件导出 |
| `:workshop-core` | 创意工坊元数据解析、`file_url` 下载、UGC manifest/chunk 下载、校验和文件组装 |
| `:steam-protocol` | Steam CM WebSocket 匿名登录、目录服务、内容服务器请求、manifest request code、CDN token 和 depot key 获取 |

构建目标：

- `minSdk 31`
- `targetSdk 36`
- `compileSdk 36`
- Java / Kotlin 字节码目标：`17`

运行时设置：

- 下载线程数可在应用设置页调整，范围 `1..8`
- 同时下载任务数可在应用设置页调整，范围 `1..3`
- 主题模式支持跟随系统、浅色和深色

<a id="release-automation"></a>

## 发布自动化

仓库已补充与参考项目一致的标签发布策略：

- `.github/workflows/release.yml` 会在推送 `v*` 标签时构建签名版 `release APK`
- 构建产物会先上传到 Actions artifact
- 如果当前引用是标签，工作流会同步创建或更新 GitHub Release
- 若存在 `docs/release/note/v<version>.md`，该文件会被用作 Release 正文

签名信息通过 GitHub Environment `release-signing` 注入，详细配置步骤见：

- `docs/release-automation/README.md`

本地准备 release note、打 tag 并推送，建议直接运行：

```powershell
scripts\prepare-release.bat
```

<a id="automation"></a>

## 自动化调试

仓库内置了面向真机调试和回归验证的 `adb` 自动化任务，适合跳过 UI 直接验证某个 `AppID + PublishedFileId` 的下载链路。

`workshopDownloadAndPullLogs` 会自动完成：

- 构建并安装最新 Debug APK
- 强制停止应用并清理目标条目的旧缓存
- 通过 `adb` 发送 `top.apricityx.workshop.action.DOWNLOAD`
- 等待下载成功、失败或超时
- 拉回 `download.log`、`logcat.txt`、`metadata.json` 和 `result.txt`

常用命令：

```powershell
.\gradlew.bat :app:workshopStartDownload -PworkshopAppId=646570 -PpublishedFileId=3677098410 -PdeviceSerial=<adb-serial>
.\gradlew.bat :app:workshopPullLogs -PworkshopAppId=646570 -PpublishedFileId=3677098410 -PdeviceSerial=<adb-serial>
.\gradlew.bat :app:workshopDownloadAndPullLogs -PworkshopAppId=646570 -PpublishedFileId=3677098410 -PdeviceSerial=<adb-serial> -PdownloadTimeoutSeconds=180 --console=plain
```

可选参数：

- `-PdeviceSerial=<adb-serial>`
- `-PdownloadTimeoutSeconds=<seconds>`
- `-PpollIntervalMillis=<milliseconds>`
- `-PlogsDir=<path>`

默认日志导出目录：

`build/workshop-adb/app-<AppID>-file-<PublishedFileId>-<timestamp>/`

<a id="repository-layout"></a>

## 仓库结构

| 路径                               | 用途                                 |
|----------------------------------|------------------------------------|
| `app/`                           | Android 主应用、Compose 页面、下载中心、公共目录导出 |
| `workshop-core/`                 | 下载引擎、元数据解析、chunk 处理、校验与组装          |
| `steam-protocol/`                | Steam 协议访问、WebSocket 会话和内容服务器客户端   |
| `gradle/workshop-adb.gradle.kts` | `adb` 调试自动化任务                      |

<a id="limitations"></a>

## 当前限制

- 仅支持公开可访问的创意工坊条目。
- 暂不支持 `Collection`。
- 工坊列表和详情依赖 Steam 页面结构与公开接口，若上游改版需要同步调整解析逻辑。
- 当网络无法稳定访问 Steam 时，游戏库、工坊列表和下载流程都可能失败或超时。
