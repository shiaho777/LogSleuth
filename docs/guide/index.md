# Getting started

## Install

Download the APK from [GitHub Releases](https://github.com/shiaho777/LogSleuth/releases/latest).
Requires Android 8.0 (API 26) or newer.

## Open a report

Tap **Open sample report**. Open **Bookmarks** to find when Pay was tapped, then
open **Crashes** to see what failed. Use **Import a log file** to replay a ZIP,
TXT, or LOG from someone else. Both paths work without log access.
**Settings → About → Quick start** reopens the welcome screen.

## Capture device logs

On the start screen, choose **Capture device logs**. You can also open
**Settings → Log access → Open setup guide**.

Android does not let an app read other apps' logs without a deliberate
grant. LogSleuth supports three, and you can change later in
**Settings → Log access**:

| Path | What it needs | What you get |
| --- | --- | --- |
| **Shizuku** | The Shizuku app, restarted after a reboot | Everything, on and off screen |
| **Root** | A rooted phone, with root switched on inside LogSleuth | Everything, on and off screen |
| **ADB grant** | `adb shell pm grant io.github.shiaho777.logsleuth.app android.permission.READ_LOGS` | Everything while LogSleuth is on screen |

**Shizuku is the recommended path** for an unrooted phone: it needs no
system modification, and it survives reboots if you restart it yourself.
LogSleuth's default *Auto* mode tries Shizuku first, then root, then the ADB
grant, so an existing Shizuku setup never triggers a root prompt.

On Android 13 and later, the ADB-granted stream must start or reconnect while
LogSleuth is on screen. An already approved stream can continue in the
background. See [Log access](/guide/log-access).

## Take the tour

From **Settings → About**, replay the guided tour to try the live controls.

## Reading the list

- **The right edge is a rail, not a scrollbar.** It draws the whole buffer as
  a stack of coloured bands — one per slice, coloured by the worst level in
  that slice — so an error cluster is visible without scrolling. Drag the
  thumb to jump, or tap a spot on the rail. While you drag, a pill shows
  `Line 1,234 of 20,000` and the percentage.
- **The badge at the rail's foot** is the total number of buffered lines.
- **The top bar** holds pause, search, the floating-controls toggle, clear,
  save, and an overflow menu with **Restart logging** for a stream that looks
  wedged. It reconnects and keeps the resume stamp, so the ring buffer is not
  dumped a second time.
- **The filter bar** narrows the stream: level, app, and the rule stack. See
  [Filters & rules](/guide/filters).

## Everyday actions

| You want to… | Do this |
| --- | --- |
| Stop the stream without losing the buffer | Tap **Pause**. The banner counts the lines that are waiting, filtered the same way the list is |
| Find something | Tap the magnifier. `Enter` jumps to the next hit |
| Copy what you see | Long-press to select a range, then copy the visible columns |
| Keep a session | **Save** in the top bar, or the tile in quick settings |
| Read a crash | The **Crashes** tab, or a notification when one is detected |
| Start over with clean settings | **Settings → Restore defaults**. It asks first, and says what it keeps |
| Report a problem | [Tell us which step got you stuck](https://github.com/shiaho777/LogSleuth/issues/new?template=bug_report.yml) |

## Next

- [Log access](/guide/log-access) — what each path can and cannot do
- [Filters & rules](/guide/filters) — narrowing a busy stream
- [Recording & crashes](/guide/recording-and-crashes) — sessions and detection
- [Automation broadcasts](/guide/automation) — log matches into Tasker/MacroDroid
- [Remote device](/guide/remote-device) — read a second device over wireless ADB
- [Local MCP](/guide/local-mcp) — record and analyze logs using an Agent in Termux
