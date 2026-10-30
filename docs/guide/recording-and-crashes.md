# Recording & crashes

Two features run in the background: a recording session that writes the
stream to a file, and a crash watcher that notices when something dies.

## Recording a session

Three ways to start one:

- **Save** in the stream top bar, which offers to save what is on screen;
- the **record tile** in quick settings;
- the floating controls.

A recording is a foreground service, so it keeps running while you use the
phone. Its notification shows the line count and offers pause, bookmark and
stop. A bookmark marks the current moment in the file, so you can find it
again later.

Size and duration limits live in **Settings → Recording**: a maximum size
(default 64 MB) and a maximum duration in hours (0 = unlimited). The session
stops by itself when a limit is reached.

Sessions are listed on the **Sessions** tab. Each one can be replayed line by
line, searched, exported as text or zip, or deleted.

## Crash and ANR detection

LogSleuth watches three kinds of failure:

| Kind | What it looks for |
| --- | --- |
| Java crash | `FATAL EXCEPTION` in the `AndroidRuntime` tag |
| Native crash | `Fatal signal` plus the tombstone markers |
| ANR | `ANR in <package>` and the `am_anr` event |

When one is detected, LogSleuth stores the event with the lines around it, so
you get the story and not just the stack. Detection can be switched per kind
in **Settings → Crash detection**, and notifications can be grouped per app or
per crash type.

**Search inside a crash.** The search on the crash screen covers the stack and
the saved surrounding logs. It has previous/next, a hit counter, and scrolls
the current match into view — the snippet first, then the context around it.
Back closes the search before it leaves the page.

**Ignore an app** if its crashes are noise: the crash detail screen can ignore
the package that crashed, and the same screen can undo it.

**Share it as text.** GitHub, Telegram and mail want `text/plain`, so the share
action sends that. A very large stack goes out as a `text/plain` file through
the system share sheet instead, and **Save** offers a `.txt` next to the zip.
Both include the surrounding logs that were saved with the crash.

## The background crash watch

With **Settings → Watch for crashes in the background** on, LogSleuth keeps a
quiet foreground service alive after you leave the app, so crashes are still
caught — including after a reboot.

Two honest limitations:

- On Android 13 and later, the ADB grant only works while LogSleuth is on
  screen, so the watch needs Shizuku or root to be useful off screen.
- If the system refuses to keep the service running, the switch says so
  instead of pretending the watch is alive. Open the app again, or switch the
  option off and on, to restart it.

## Sharing and export

| What | How |
| --- | --- |
| A crash | The crash detail screen shares the stack as plain text, or saves a zip with the device and app context |
| A session | The session list exports text or zip |
| What you are looking at | The **Report** tab walks you through a guided export |
| The SDK's files | The SDK packages its own logs into a zip you can share |

Text share stays under the binder size limit; a very large stack goes out as
a `text/plain` file through the system share sheet instead. Exports include
the surrounding logs that were saved with a crash.
