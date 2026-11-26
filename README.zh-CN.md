<div align="center">
  <h1>LogSleuth</h1>
  <img src="docs/screenshots/app-icon.svg" width="88" height="88" alt="LogSleuth 图标，日志行上的放大镜">
  <p><strong>找到安卓故障前后的日志，把它保存成可以回放的会话。</strong></p>
  <p>可以看实时 Logcat、录下复现过程，也可以打开别人发来的报告。内嵌 SDK 能在你自己的应用里采集日志、崩溃和 ANR。</p>
  <p>
    <a href="https://github.com/shiaho777/LogSleuth/releases/latest">下载 APK</a> ·
    <a href="#快速上手">快速上手</a> ·
    <a href="#集成-sdk">集成 SDK</a> ·
    <a href="README.md">English</a>
  </p>
  <p>
    <a href="https://github.com/shiaho777/LogSleuth/actions/workflows/ci.yml"><img src="https://github.com/shiaho777/LogSleuth/actions/workflows/ci.yml/badge.svg" alt="查看构建和测试状态"></a>
    <a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache--2.0-245c52" alt="阅读 Apache 2.0 许可证"></a>
  </p>
</div>

## 安装

LogSleuth 支持 **Android 8.0（API 26）及以上**。日常使用从 [GitHub Releases](https://github.com/shiaho777/LogSleuth/releases/latest) 下载名为 `LogSleuth-*.APK` 的安装包。

下方图库来自当前源码版。要体验首次启动的示例报告和图中的界面，可以[从源码构建](#构建与贡献)。稳定版 v0.4.2 APK 使用上一版界面。

## 截图

<p align="center">
  <a href="docs/screenshots/16-welcome.png"><img src="docs/screenshots/16-welcome.png" width="156" alt="首次启动入口，可打开示例、导入文件或采集日志"></a>
  <a href="docs/screenshots/05-session-replay.png"><img src="docs/screenshots/05-session-replay.png" width="156" alt="结账示例报告，显示日志时间线、崩溃和书签标签页"></a>
  <a href="docs/screenshots/12-report.png"><img src="docs/screenshots/12-report.png" width="156" alt="采集完成的报告，可预览、分享或保留会话"></a>
</p>

<p align="center">首次启动 · 会话回放 · 采集完成。点击图片可查看原图。</p>

<details>
<summary>首次使用与日志权限</summary>

<p align="center">
  <a href="docs/screenshots/11-setup.png"><img src="docs/screenshots/11-setup.png" width="206" alt="日志访问配置，包含 Shizuku 和 ADB 选项"></a>
  <a href="docs/screenshots/14-tour.png"><img src="docs/screenshots/14-tour.png" width="206" alt="引导式导览，可继续或跳过"></a>
  <a href="docs/screenshots/25-import.png"><img src="docs/screenshots/25-import.png" width="206" alt="SDK 示例分享的 ZIP 已在 LogSleuth 中打开为导入会话"></a>
</p>

</details>

<details>
<summary>实时日志、筛选、搜索与多选</summary>

<p align="center">
  <a href="docs/screenshots/01-live-stream.png"><img src="docs/screenshots/01-live-stream.png" width="206" alt="实时 Logcat，已筛选 Checkout 标签"></a>
  <a href="docs/screenshots/02-filters.png"><img src="docs/screenshots/02-filters.png" width="206" alt="展开日志筛选，显示标签、关键词、排除条件和正则开关"></a>
  <a href="docs/screenshots/07-search.png"><img src="docs/screenshots/07-search.png" width="206" alt="实时搜索高亮支付授权相关日志，并显示匹配项导航"></a>
</p>

<p align="center">
  <a href="docs/screenshots/09-selection.png"><img src="docs/screenshots/09-selection.png" width="206" alt="日志多选模式与复制工具栏"></a>
  <a href="docs/screenshots/10-scope-picker.png"><img src="docs/screenshots/10-scope-picker.png" width="206" alt="保存至会话对话框，已选择按应用限定范围"></a>
</p>

</details>

<details>
<summary>会话回放、日志详情与书签</summary>

<p align="center">
  <a href="docs/screenshots/04-sessions.png"><img src="docs/screenshots/04-sessions.png" width="206" alt="会话列表，包含导入报告、录制记录和内置示例"></a>
  <a href="docs/screenshots/20-bookmarks.png"><img src="docs/screenshots/20-bookmarks.png" width="206" alt="书签标签页，记有示例用户点击支付的时刻"></a>
  <a href="docs/screenshots/26-entry-message.png"><img src="docs/screenshots/26-entry-message.png" width="206" alt="日志详情的消息标签页，可复制消息"></a>
</p>

<p align="center">
  <a href="docs/screenshots/08-entry-detail.png"><img src="docs/screenshots/08-entry-detail.png" width="206" alt="日志详情的原始日志页，显示进程字段与复制操作"></a>
</p>

<p align="center">
  <a href="docs/screenshots/21-wide-detail.png"><img src="docs/screenshots/21-wide-detail.png" width="640" alt="横屏会话视图，日志时间线与详情并排"></a>
</p>

</details>

<details>
<summary>复现故障、查看崩溃与分享</summary>

<p align="center">
  <a href="docs/screenshots/17-report-app.png"><img src="docs/screenshots/17-report-app.png" width="206" alt="引导式上报的应用选择页，已选中 Sleuth Sample"></a>
  <a href="docs/screenshots/18-report-recording.png"><img src="docs/screenshots/18-report-recording.png" width="206" alt="上报录制中，显示实时计数和打开目标应用的入口"></a>
  <a href="docs/screenshots/03-crashes.png"><img src="docs/screenshots/03-crashes.png" width="206" alt="按应用分组的崩溃与 ANR 列表"></a>
</p>

<p align="center">
  <a href="docs/screenshots/19-crash-detail.png"><img src="docs/screenshots/19-crash-detail.png" width="206" alt="崩溃详情，显示结账异常及堆栈"></a>
  <a href="docs/screenshots/15-share-sheet.png"><img src="docs/screenshots/15-share-sheet.png" width="206" alt="录制保存后的底部面板，可分享 TXT 或 ZIP"></a>
</p>

</details>

<details>
<summary>设置、远程采集、自动化与 SDK</summary>

<p align="center">
  <a href="docs/screenshots/13-settings.png"><img src="docs/screenshots/13-settings.png" width="206" alt="设置页，显示当前设备与日志来源选项"></a>
  <a href="docs/screenshots/23-remote-adb.png"><img src="docs/screenshots/23-remote-adb.png" width="206" alt="远程 ADB 设置，显示主机与端口字段"></a>
  <a href="docs/screenshots/22-automation.png"><img src="docs/screenshots/22-automation.png" width="206" alt="自动化规则编辑器，可设置匹配条件、目标应用和冷却时间"></a>
</p>

<p align="center">
  <a href="docs/screenshots/24-dark-theme.png"><img src="docs/screenshots/24-dark-theme.png" width="206" alt="深色主题下的结账示例报告"></a>
  <a href="docs/screenshots/06-sdk-sample.png"><img src="docs/screenshots/06-sdk-sample.png" width="206" alt="SDK 示例应用，可生成测试日志并分享 ZIP"></a>
</p>

</details>

[截图索引与采集信息](docs/screenshots/README.md)

## 快速上手

1. **打开第一份报告。** 源码版首屏可选「打开示例报告」，查看一次虚构的结账崩溃，也可选「导入日志文件」打开 ZIP、TXT 或 LOG。使用 v0.4.2 时，跳过设置向导再从「会话」导入。这两条体验路径都无需设备日志权限。
2. **沿时间线找故障。** 打开一个会话，在「日志」「崩溃」「书签」间切换。示例里的「点击支付」书签可以跳到附近的日志。点开一行，可在消息和原始日志之间切换。
3. **开始自己的采集。** 源码版首屏选择「采集设备日志」，也可以从「设置」打开设置向导。先筛选再录制，保存后点「预览日志」，确认内容再分享 TXT 或 ZIP。想跟踪一个特定应用时，可以从「上报」进入引导式录制。

### 选择日志来源

| 来源 | 适用场景 |
| --- | --- |
| **Shizuku** | 推荐给无 Root 的手机。启动 Shizuku 后授权 LogSleuth，可以按应用筛选，并在后台持续采集。 |
| **ADB 授权** | 有电脑时可用来读取本机日志。Android 13 及以上启动或重连日志流时，请让查看器保持在前台，以便确认系统授权。 |
| **Root** | 适用于已经 Root 的设备。在设置向导中主动开启「使用 Root」。 |
| **远程 ADB** | 源码版可通过传统 TCP ADB，在可信网络中读取第二台安卓设备的日志。 |

详细步骤见[日志权限指南](docs/zh/guide/log-access.md)和[远程设备指南](docs/zh/guide/remote-device.md)。

## 可以用它做什么

- **找到关键行。** 按级别、Tag、文本或正则筛选，组合包含和排除规则。可以在搜索结果间跳转，也可以用右侧的日志轨道寻找错误集中的位置。
- **保留故障前后。** 保存快照，或录制当前筛选后的日志流。复现时可从通知或可选的悬浮控件添加书签，稍后回到会话里查看那一刻的上下文。
- **排查崩溃和卡死。** 查看 Java 崩溃、原生崩溃和 ANR，阅读堆栈与已有的上下文日志。保存记录和通知提醒可以分别设置。
- **发出可回放的报告。** 预览会话后分享 TXT 或 ZIP，也可以把 SDK 导出的 ZIP 导回查看器。启用[自动化规则](docs/zh/guide/automation.md)后，可把匹配的日志行发送给自己选定的应用。

详细操作见[筛选与预设](docs/zh/guide/filters.md)和[录制与崩溃检测](docs/zh/guide/recording-and-crashes.md)。

## 集成 SDK

SDK 只采集宿主应用的日志、Java 崩溃和 ANR。它不声明安卓权限，不发起网络请求，还可以在落盘前按配置脱敏。用户无需安装 LogSleuth 查看器，也能从你的应用分享 ZIP。

当前稳定版 Release 提供查看器 APK。接入可先看仓库里的[示例应用](sample/)和 [SDK 源码模块](sdk/)。两者位于同一个 Gradle 构建时，在应用中加入模块依赖。

```kotlin
implementation(project(":sdk"))
```

在 `Application` 里初始化。如果新建了子类，请在 Manifest 的 `<application>` 上设置 `android:name=".MyApp"`。

```kotlin
import android.app.Application
import io.github.shiaho777.logsleuth.sdk.Sleuth

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Sleuth.init(this)
    }
}
```

在 Activity 的帮助或反馈入口调用以下方法。

```kotlin
Sleuth.shareLogs(this)
```

如果要通过 Maven 产物验证接入，可以发布到当前检出的本地暂存仓库，再用该产物构建示例。[SDK 构建和发布说明](docs/developer/release.md#sdk-smoke-build)

## 构建与贡献

使用与 CI 一致的 **JDK 21**，并安装 **Android SDK Platform 36**。

```bash
git clone https://github.com/shiaho777/LogSleuth.git
cd LogSleuth
python3 scripts/check_string_parity.py
./gradlew assembleDebug test lint
```

查看器 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`，SDK 示例 APK 位于 `sample/build/outputs/apk/debug/`。欢迎改进查看器、SDK、测试或翻译，参加前可看[贡献指南](CONTRIBUTING.md)和[开发者指南](docs/developer/index.md)。

## 帮助与隐私

- **看不到日志或应用名？** 先在「设置」里检查当前来源。[日志权限指南](docs/zh/guide/log-access.md)说明各来源的授权、重连行为，以及哪些来源能把日志关联到应用。
- **遇到问题？** 请提供 LogSleuth 版本、安卓版本、日志来源和复现步骤。附日志或截图前记得去掉私人信息。[提交 Bug](https://github.com/shiaho777/LogSleuth/issues/new?template=bug_report.yml)
- **日志会去哪里？** 采集文件保存在设备上。你使用导出或自动化功能时才会有对应的数据传出。查看器联网用于更新和你配置的远程日志源。
- **其他入口。** [使用指南](https://shiaho777.github.io/LogSleuth/zh/guide/) · [问题列表](https://github.com/shiaho777/LogSleuth/issues) · [QQ 群 1127256026](https://qm.qq.com/q/Gzj7CpirAs) · [Bilibili](https://space.bilibili.com/2127032909)

## 许可证

[Apache License 2.0](LICENSE)。本文使用项目自己的图标，以及查看器和 SDK 示例的界面截图。
