# Log access

Android treats the device log as private. An app can read its own logs
freely; reading anyone else's needs a grant that the user gives on purpose.
LogSleuth supports three, and they are not equivalent.

## The three paths

### Shizuku

Shizuku is a service that keeps ADB's privileges alive outside of a
computer. LogSleuth talks to it over a normal binder service.

- Needs the Shizuku app, started (it stops on reboot — start it again, or set
  up Shizuku's own auto-start).
- Reads **every** buffer, including other apps' logs.
- Keeps working when LogSleuth is in the background, so the crash watch and
  the floating controls both work.
- No system modification, so it is the right choice on an unrooted phone.

### Root

LogSleuth can run a shell as `su` through [libsu](https://github.com/topjohnwu/libsu).

- Opt-in, and off by default: the app never executes `su` until you switch it
  on in **Settings → Log access**.
- Reads everything, and keeps working off screen.
- The *Auto* mode prefers Shizuku, so an existing Shizuku setup never opens a
  root shell.

### ADB grant

With a computer attached, or from a terminal on the device:

```bash
adb shell pm grant io.github.shiaho777.logsleuth android.permission.READ_LOGS
```

- No root, no Shizuku, but the grant is tied to the installed build.
- **Android 13 and later:** a logcat started as an app only reads while the
  app is on screen. The system declines a background start and remembers the
  decline for about a minute, which is why the approval prompt can seem to
  appear late. Shizuku and root are not affected.
- On Android 12 and earlier the stream keeps running in the background.

## Choosing one

*Settings → Log access* has one row per path, with the current choice marked.

- **Auto** — try Shizuku, then root, then the ADB grant. This is the default
  and the right answer for most people.
- **Shizuku** — force Shizuku. Use it when you want the app to stay quiet
  instead of ever suggesting root.
- **Root** — force a root shell.
- **ADB grant** — use only the system grant.

## When the stream does not start

| Symptom | Cause | Fix |
| --- | --- | --- |
| "Waiting for logs" forever, no other app's lines | The grant is missing or was declined | Check **Settings → Log access**; a declined background start is remembered for about a minute, so wait and try again |
| Own logs only | Running on the ADB grant on Android 13+ while off screen | Bring LogSleuth to the foreground, or switch to Shizuku or root |
| Shizuku shows as disconnected after a reboot | Shizuku stops with the phone | Start Shizuku again, then pull LogSleuth back to the foreground |
| "Permission denied" from a root shell | Root is not granted to LogSleuth | Grant it in your root manager, then toggle root off and on in LogSleuth |

## What is read

LogSleuth streams the stock logcat buffer set:

```text
-b main -b system -b crash -b events
```

`radio` stays out — it is noisy and rarely useful. Each line carries the uid
column, which is what lets the app group lines by app. Turning a uid into a
package name needs a shell, so package labels appear on the Shizuku and root
paths.

## Permissions the app itself asks for

| Permission | Why |
| --- | --- |
| `READ_LOGS` | Reading the device log (optional, via the ADB grant) |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | The crash watch and the recording service |
| `POST_NOTIFICATIONS` | Crash notifications and the two foreground notifications |
| `SYSTEM_ALERT_WINDOW` | The optional floating controls |
| `RECEIVE_BOOT_COMPLETED` | Restarting the crash watch after a reboot, if you left it on |
| `INTERNET`, `REQUEST_INSTALL_PACKAGES` | The in-app update check and the APK download |

The SDK (`logsleuth-sdk`) asks for **none** of them. It only ever reads its
own host app's logs.
