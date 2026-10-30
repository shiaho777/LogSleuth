# Architecture

## The whole picture

```text
LogcatSource (local / Shizuku / root)
  → LogcatEngine (singleton, owns THE logcat process)
      SharedFlow<LogcatEntry> ─┬→ StreamViewModel (per-frame batches) → UI list
                                ├→ RecordingManager → session file + Room row
                                └→ CrashDetector → CrashRecorder → crash_events + notification

logsleuth-sdk (inside a host app, no permissions):
  Sleuth.init → LogcatCaptureThread (logcat --pid=self)
              + CrashHandler (chained UncaughtExceptionHandler)
              + AnrWatchdog (main-looper heartbeat)
                → LogFileStore (ring files, redaction) → LogSharer (zip share)
```

## The engine

`LogcatEngine` is a `@Singleton` that owns the only `logcat` process on the
device. It is reference counted: the UI, the recording service and the crash
watch each hold a client, and the process lives while at least one does.

- **Access** — `AccessPolicy` decides the path: `auto` tries Shizuku, then
  root, then the ADB grant. Root is opt-in; the app never executes `su`
  until the user switches it on.
- **Buffers** — the command reads `-b main -b system -b crash -b events`.
  The `uid` column is requested so the client can group lines by app.
- **Reconnects** — a dead process is restarted with backoff and with `-T`
  pointing at the last stamp, so a reconnect does not dump the ring buffer
  again.
- **Android 13+** — a logcat started as an app is declined while the app is in
  the background, and the decline is cached for about a minute. Shizuku and
  root run as the shell user and are not affected.

## The stream

`StreamViewModel` consumes the flow in **per-frame batches**: emissions are
collected and flushed on a 16 ms frame, not one by one. The list is a
`SnapshotStateList` that is appended to and has its prefix dropped when the
ring buffer evicts rows — it never copies itself.

The pure side of the stream lives in `core/`:

| Piece | File | What it does |
| --- | --- | --- |
| `LogcatParser` | `core/logcat/` | threadtime + uid → `LogcatEntry`, with the year-boundary fix |
| `EntryAssembler` | `core/logcat/` | Turns parsed lines into entries with stable ids |
| `LogFilter` / `CompiledFilter` | `core/filter/` | The filter model and its compiled matcher |
| `CrashDetector` | `core/detect/` | The crash / native crash / ANR state machine |
| `SessionExporter` / `ImportCore` | `core/export/`, `core/importer/` | Text and zip export, and the import path |
| `ScrollRailMath` | `ui/stream/` | The rail's geometry — pure, so the drag mapping is testable |

## The UI

`StreamScreen.kt` owns state and gestures. The pure chrome — top bars,
banners, the jump buttons, the selection bar, the saved-session sheet — lives
in `StreamChrome.kt`, so those pieces can be reasoned about on their own.

`LogScrollRail.kt` is the right-edge rail. It draws a severity fingerprint of
the whole buffer, a thumb sized to the viewport that can be dragged or
tapped, and a `Line N of M` readout. Its geometry is in `ScrollRailMath`, so
the drag mapping is unit tested; the rail itself only reports an index.

## Crash handling

`CrashDetector` is a pure state machine over the log stream. It decides
*that* a crash happened and hands over a buffer snapshot;
`CrashRecorder` (in `core/crashes/`) owns persistence, the surrounding-log
capture and the notification. Keeping the split means the engine file stays
about the process.

## The SDK

The SDK runs in a different process entirely — the host app's — and may only
read its own logs. It must never assume the viewer app is installed, never
ask for a permission, and never touch the network.

- `LogcatCaptureThread` reads `logcat --pid=self`.
- `CrashHandler` chains the host's existing `UncaughtExceptionHandler`.
- `AnrWatchdog` watches the main looper's heartbeat.
- `LogFileStore` keeps redacted ring files; `LogSharer` zips them for the
  user to share.
