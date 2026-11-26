<div align="center">
  <h1>LogSleuth</h1>
  <img src="docs/screenshots/app-icon.svg" width="88" height="88" alt="LogSleuth’s magnifier over log lines">
  <p><strong>Find the Android logs around a failure, then open them again as a session.</strong></p>
  <p>Watch live Logcat, record a reproduction, or open a report someone sent you. The embedded SDK captures logs, crashes, and ANRs inside your own app.</p>
  <p>
    <a href="https://github.com/shiaho777/LogSleuth/releases/latest">Download the APK</a> ·
    <a href="#quick-start">Quick start</a> ·
    <a href="#embedding-the-sdk">Embed the SDK</a> ·
    <a href="README.zh-CN.md">简体中文</a>
  </p>
  <p>
    <a href="https://github.com/shiaho777/LogSleuth/actions/workflows/ci.yml"><img src="https://github.com/shiaho777/LogSleuth/actions/workflows/ci.yml/badge.svg" alt="View build and test status"></a>
    <a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache--2.0-245c52" alt="Read the Apache 2.0 license"></a>
  </p>
</div>

## Install

LogSleuth runs on **Android 8.0 (API 26) and newer**. For a regular installation, download the `LogSleuth-*.APK` asset from [GitHub Releases](https://github.com/shiaho777/LogSleuth/releases/latest).

The gallery shows the current source build. For the sample report on first launch and the interface shown below, [build from source](#build-and-contribute). The stable v0.4.2 APK uses the previous interface.

## Screenshots

Select any screenshot to see it at full size.

<table align="center">
  <tr>
    <td width="25%" align="center" valign="top"><a href="docs/screenshots/viewer-fd19e78/01-welcome.png"><img src="docs/screenshots/viewer-fd19e78/01-welcome.png" width="128" alt="The welcome screen offers a sample report, file import, and device capture."></a><br><sub>First launch</sub></td>
    <td width="25%" align="center" valign="top"><a href="docs/screenshots/viewer-fd19e78/02-log-access.png"><img src="docs/screenshots/viewer-fd19e78/02-log-access.png" width="128" alt="Choose Shizuku, root, or an ADB grant."></a><br><sub>Log access</sub></td>
    <td width="25%" align="center" valign="top"><a href="docs/screenshots/viewer-fd19e78/03-android-approval.png"><img src="docs/screenshots/viewer-fd19e78/03-android-approval.png" width="128" alt="Android asks for one-time access to device logs after an ADB grant."></a><br><sub>Android approval</sub></td>
    <td width="25%" align="center" valign="top"><a href="docs/screenshots/viewer-fd19e78/04-live-home.png"><img src="docs/screenshots/viewer-fd19e78/04-live-home.png" width="128" alt="The main Logs tab is streaming with all levels and apps visible."></a><br><sub>Live logs home</sub></td>
  </tr>
  <tr>
    <td width="25%" align="center" valign="top"><a href="docs/screenshots/viewer-fd19e78/05-guided-tour.png"><img src="docs/screenshots/viewer-fd19e78/05-guided-tour.png" width="128" alt="The tour explains the controls directly on the live screen."></a><br><sub>Guided tour</sub></td>
    <td width="25%" align="center" valign="top"><a href="docs/screenshots/viewer-fd19e78/06-filter-controls.png"><img src="docs/screenshots/viewer-fd19e78/06-filter-controls.png" width="128" alt="Set a tag, keywords, exclusions, or regular expressions."></a><br><sub>Filter controls</sub></td>
    <td width="25%" align="center" valign="top"><a href="docs/screenshots/viewer-fd19e78/07-filtered-live-logs.png"><img src="docs/screenshots/viewer-fd19e78/07-filtered-live-logs.png" width="128" alt="Follow live checkout logs with the Checkout tag active."></a><br><sub>Filtered logs</sub></td>
    <td width="25%" align="center" valign="top"><a href="docs/screenshots/viewer-fd19e78/08-log-search.png"><img src="docs/screenshots/viewer-fd19e78/08-log-search.png" width="128" alt="Search for payment authorization and jump between matches."></a><br><sub>Search matches</sub></td>
  </tr>
  <tr>
    <td width="25%" align="center" valign="top"><a href="docs/screenshots/viewer-fd19e78/09-select-log-lines.png"><img src="docs/screenshots/viewer-fd19e78/09-select-log-lines.png" width="128" alt="Select log lines and copy them together."></a><br><sub>Select log lines</sub></td>
    <td width="25%" align="center" valign="top"><a href="docs/screenshots/viewer-fd19e78/10-save-scope.png"><img src="docs/screenshots/viewer-fd19e78/10-save-scope.png" width="128" alt="Choose which apps to include in a saved session."></a><br><sub>Choose save scope</sub></td>
    <td width="25%" align="center" valign="top"><a href="docs/screenshots/viewer-fd19e78/11-recording-saved.png"><img src="docs/screenshots/viewer-fd19e78/11-recording-saved.png" width="128" alt="Preview a saved recording or share it as ZIP or TXT."></a><br><sub>Share logs</sub></td>
    <td width="25%" align="center" valign="top"><a href="docs/screenshots/viewer-fd19e78/12-sessions.png"><img src="docs/screenshots/viewer-fd19e78/12-sessions.png" width="128" alt="Browse recordings, imports, and built-in examples."></a><br><sub>Session library</sub></td>
  </tr>
  <tr>
    <td width="25%" align="center" valign="top"><a href="docs/screenshots/viewer-fd19e78/13-session-replay.png"><img src="docs/screenshots/viewer-fd19e78/13-session-replay.png" width="128" alt="Read the fictional checkout crash in its original timeline."></a><br><sub>Replay a session</sub></td>
    <td width="25%" align="center" valign="top"><a href="docs/screenshots/viewer-fd19e78/14-bookmarks.png"><img src="docs/screenshots/viewer-fd19e78/14-bookmarks.png" width="128" alt="The Tapped Pay bookmark jumps to the nearby logs."></a><br><sub>Jump to Pay</sub></td>
    <td width="25%" align="center" valign="top"><a href="docs/screenshots/viewer-fd19e78/15-log-message.png"><img src="docs/screenshots/viewer-fd19e78/15-log-message.png" width="128" alt="Read and copy the message in the log inspector."></a><br><sub>Inspect a message</sub></td>
    <td width="25%" align="center" valign="top"><a href="docs/screenshots/viewer-fd19e78/16-raw-log-line.png"><img src="docs/screenshots/viewer-fd19e78/16-raw-log-line.png" width="128" alt="Switch to the raw log and copy the original line."></a><br><sub>View the raw line</sub></td>
  </tr>
  <tr>
    <td colspan="4" align="center" valign="top"><a href="docs/screenshots/viewer-fd19e78/17-landscape-inspector.png"><img src="docs/screenshots/viewer-fd19e78/17-landscape-inspector.png" width="550" alt="A landscape session keeps the timeline and inspector visible together."></a><br><sub>Inspect logs in landscape</sub></td>
  </tr>
  <tr>
    <td width="25%" align="center" valign="top"><a href="docs/screenshots/viewer-fd19e78/18-report-target.png"><img src="docs/screenshots/viewer-fd19e78/18-report-target.png" width="128" alt="The report wizard is scoped to Sleuth Sample."></a><br><sub>Pick the report app</sub></td>
    <td width="25%" align="center" valign="top"><a href="docs/screenshots/viewer-fd19e78/19-report-recording.png"><img src="docs/screenshots/viewer-fd19e78/19-report-recording.png" width="128" alt="A recording shows a live line count and can reopen the selected app."></a><br><sub>Record the bug</sub></td>
    <td width="25%" align="center" valign="top"><a href="docs/screenshots/viewer-fd19e78/20-report-ready.png"><img src="docs/screenshots/viewer-fd19e78/20-report-ready.png" width="128" alt="The report shows the captured crash and offers preview, share, and save actions."></a><br><sub>Captured report</sub></td>
    <td width="25%" align="center" valign="top"><a href="docs/screenshots/viewer-fd19e78/21-report-preview.png"><img src="docs/screenshots/viewer-fd19e78/21-report-preview.png" width="128" alt="Review a captured report in the session reader before sharing it."></a><br><sub>Preview the report</sub></td>
  </tr>
  <tr>
    <td width="25%" align="center" valign="top"><a href="docs/screenshots/viewer-fd19e78/22-crash-list.png"><img src="docs/screenshots/viewer-fd19e78/22-crash-list.png" width="128" alt="The Crashes tab groups reports by app."></a><br><sub>Browse crash groups</sub></td>
    <td width="25%" align="center" valign="top"><a href="docs/screenshots/viewer-fd19e78/23-crash-stack.png"><img src="docs/screenshots/viewer-fd19e78/23-crash-stack.png" width="128" alt="Open the Sleuth Sample crash and read the exception stack."></a><br><sub>Read the stack trace</sub></td>
    <td width="25%" align="center" valign="top"><a href="docs/screenshots/viewer-fd19e78/24-settings.png"><img src="docs/screenshots/viewer-fd19e78/24-settings.png" width="128" alt="View the active log source and choose a preferred access method."></a><br><sub>Log source settings</sub></td>
    <td width="25%" align="center" valign="top"><a href="docs/screenshots/viewer-fd19e78/25-remote-adb.png"><img src="docs/screenshots/viewer-fd19e78/25-remote-adb.png" width="128" alt="Set the host and port of a device to connect to over ADB."></a><br><sub>Remote ADB</sub></td>
  </tr>
  <tr>
    <td width="25%" align="center" valign="top"><a href="docs/screenshots/viewer-fd19e78/26-automation-rule.png"><img src="docs/screenshots/viewer-fd19e78/26-automation-rule.png" width="128" alt="Define matching conditions, a target app, and the cooldown."></a><br><sub>Add a rule</sub></td>
    <td width="25%" align="center" valign="top"><a href="docs/screenshots/viewer-fd19e78/27-dark-replay.png"><img src="docs/screenshots/viewer-fd19e78/27-dark-replay.png" width="128" alt="Replay the sample session in the dark theme."></a><br><sub>Dark theme</sub></td>
    <td width="25%" align="center" valign="top"><a href="docs/screenshots/viewer-fd19e78/28-sdk-sample.png"><img src="docs/screenshots/viewer-fd19e78/28-sdk-sample.png" width="128" alt="Generate test logs in the sample and share an SDK ZIP."></a><br><sub>SDK sample app</sub></td>
    <td width="25%" align="center" valign="top"><a href="docs/screenshots/viewer-fd19e78/29-sdk-import.png"><img src="docs/screenshots/viewer-fd19e78/29-sdk-import.png" width="128" alt="Open the SDK ZIP as an imported session and filter the test logs."></a><br><sub>Import the SDK ZIP</sub></td>
  </tr>
</table>

[Screenshot capture details](docs/screenshots/README.md)

## Quick start

1. **Open a report.** In the source build, choose **Open sample report** to explore a fictional checkout crash, or **Import a log file** to open a ZIP, TXT, or LOG. On v0.4.2, skip the setup wizard and import from **Sessions**. These paths work without device log access.
2. **Read the timeline.** In **Sessions**, open a report and switch between **Logs**, **Crashes**, and **Bookmarks**. In the sample, **Tapped Pay** jumps to the nearby log line. Tap a log to switch between its message and raw line.
3. **Capture your own logs.** Choose **Capture device logs** on the source build’s start screen, or open the setup guide from **Settings**. Filter the live stream, start a recording, and use **Preview logs** before sharing TXT or ZIP. In **Report**, pick a target app to follow the guided capture flow.

### Choose a log source

| Source | When to use it |
| --- | --- |
| **Shizuku** | Recommended for an unrooted phone. Start Shizuku and grant LogSleuth access. It supports per-app filtering and background capture. |
| **ADB grant** | For local device logs with a computer. On Android 13+, keep the viewer on screen when the stream starts or reconnects so Android can show the approval prompt. |
| **Root** | For a device that already has root. Enable **Use root** explicitly in the setup guide. |
| **Remote ADB** | On the source build, read a second Android device on a trusted network using classic TCP ADB. |

Read the [log access guide](docs/guide/log-access.md) for setup and [the remote-device guide](docs/guide/remote-device.md) for the second-device flow.

## What you can do

- **Find the lines that matter.** Filter by level, tag, text, or regex. Combine including and excluding rules. Jump between search matches or use the right-edge rail to find clusters of errors in the buffer.
- **Keep the context.** Save a snapshot or record the filtered stream. Bookmark the moment from the notification or the optional floating controls, then reopen the session to read the surrounding lines.
- **Investigate crashes and freezes.** Review Java crashes, native crashes, and ANRs. Open the stack and available surrounding logs. Configure saving and notifications separately.
- **Send a report someone can open.** Preview a session, share TXT or ZIP, or import a ZIP from the embedded SDK back into the viewer. The optional [automation rules](docs/guide/automation.md) can send matching lines to an app you choose.

For more detail, see [filters and presets](docs/guide/filters.md) and [recording and crash detection](docs/guide/recording-and-crashes.md).

## Embedding the SDK

The SDK captures its host app’s logs, Java crashes, and ANRs. It declares no Android permissions, makes no network requests, and can redact configured patterns before writing logs. A user can share a ZIP from your app even if the LogSleuth viewer is absent.

The current stable Release ships the viewer APK. Start with the included [sample app](sample/) and the [SDK source module](sdk/). When both modules are in the same Gradle build, add the project dependency in your app.

```kotlin
implementation(project(":sdk"))
```

Initialize from your `Application`. If you create a new subclass, register it with `android:name=".MyApp"` on your manifest’s `<application>` element.

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

In an Activity, call this from your support or feedback action.

```kotlin
Sleuth.shareLogs(this)
```

To test integration through a Maven artifact, publish to the checkout’s local staging repository and run the sample against it. [SDK build and publishing notes](docs/developer/release.md#sdk-smoke-build)

## Build and contribute

Use **JDK 21** to match CI and install **Android SDK platform 36**.

```bash
git clone https://github.com/shiaho777/LogSleuth.git
cd LogSleuth
python3 scripts/check_string_parity.py
./gradlew assembleDebug test lint
```

The viewer APK is written to `app/build/outputs/apk/debug/app-debug.apk`. The SDK sample APK is under `sample/build/outputs/apk/debug/`. See the [contribution guide](CONTRIBUTING.md) for changes to the viewer, SDK, tests, or translations, and the [developer guide](docs/developer/index.md) for architecture.

## Help and privacy

- **Missing logs or app names?** Check the active source in **Settings**. The [log access guide](docs/guide/log-access.md) explains the grant, restart behavior, and which sources can associate logs with apps.
- **Reporting a bug?** Include the LogSleuth version, Android version, log source, and steps to reproduce. Remove private information from any logs or screenshots you attach. [Open a bug report](https://github.com/shiaho777/LogSleuth/issues/new?template=bug_report.yml)
- **Where does the data go?** Captured files stay on the device. Export and automation send data when you use those features. The viewer uses the network for updates and for a remote source you configure.
- **More help.** [User guide](https://shiaho777.github.io/LogSleuth/guide/) · [Issues](https://github.com/shiaho777/LogSleuth/issues) · [QQ 1127256026](https://qm.qq.com/q/Gzj7CpirAs) · [Bilibili](https://space.bilibili.com/2127032909)

## License

[Apache License 2.0](LICENSE). The README uses the project’s app icon and captures of the LogSleuth viewer and SDK sample.
