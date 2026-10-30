# Getting started

LogSleuth reads the device log. The first minute is about granting it a way
to do that; after that it is a live list you can filter, record and export.

## 1. Install

Grab the APK from
[GitHub Releases](https://github.com/shiaho777/LogSleuth/releases/latest),
[F-Droid](https://f-droid.org/packages/io.github.shiaho777.logsleuth/) or the
[Play Store](https://play.google.com/store/apps/details?id=io.github.shiaho777.logsleuth).
Android 8.0 (API 26) or newer.

## 2. Grant log access

Android does not let an app read other apps' logs without a deliberate
grant. LogSleuth supports three, and you can change later in
**Settings → Log access**:

| Path | What it needs | What you get |
| --- | --- | --- |
| **Shizuku** | The Shizuku app, restarted after a reboot | Everything, on and off screen |
| **Root** | A rooted phone, with root switched on inside LogSleuth | Everything, on and off screen |
| **ADB grant** | `adb shell pm grant io.github.shiaho777.logsleuth android.permission.READ_LOGS` | Everything while LogSleuth is on screen |

**Shizuku is the recommended path** for an unrooted phone: it needs no
system modification, and it survives reboots if you restart it yourself.
LogSleuth's default *Auto* mode tries Shizuku first, then root, then the ADB
grant, so an existing Shizuku setup never triggers a root prompt.

The ADB grant has one honest limitation: since Android 13, a logcat started
as your app only works while LogSleuth is on screen. Leave the app and the
stream stops. See [Log access](/guide/log-access) for the details.

## 3. Take the tour

The first launch walks through the three access paths and the main screen.
Skip it if you like — **Settings → Feature tour** replays it.

## Reading the list

- **The right edge is a rail, not a scrollbar.** It draws the whole buffer as
  a stack of coloured bands — one per slice, coloured by the worst level in
  that slice — so an error cluster is visible without scrolling. Drag the
  thumb to jump, or tap a spot on the rail. While you drag, a pill shows
  `Line 1,234 of 20,000` and the percentage.
- **The badge at the rail's foot** is the total number of buffered lines.
- **The top bar** holds pause, search, the floating-controls toggle, clear,
  save, and an overflow menu with **Restart logging** for a stream that looks
  wedged.
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

## Next

- [Log access](/guide/log-access) — what each path can and cannot do
- [Filters & rules](/guide/filters) — narrowing a busy stream
- [Recording & crashes](/guide/recording-and-crashes) — sessions and detection
