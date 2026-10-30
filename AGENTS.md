# AGENTS.md

Instructions for coding agents working in this repository.

## Code style

- Do what you believe is right. Make the change complete and correct, not the
  smallest possible diff. If a fix calls for refactoring, renaming, or touching
  multiple files, do it.
- Match the patterns and conventions already in the surrounding code.
- Do not add copyright or license headers unless asked.
- Avoid new third-party dependencies unless strongly justified. The `:sdk`
  module especially must stay minimal — permission-free, network-free.

## Project layout

| Path | Role |
|------|------|
| `app/` | The LogSleuth viewer app: logcat engine, UI, recording, detection. |
| `sdk/` | `logsleuth-sdk` — the embeddable zero-permission logging library other apps integrate. |
| `sample/` | Demo app consuming `:sdk`; used for manual verification of the SDK loop. |
| `scripts/` | Repo gates (`check_string_parity.py`). |
| `docs/screenshots/` | README screenshot set (15 real device captures, both READMEs). |
| `fastlane/metadata/` | Store listing copy (en-US + zh-CN), shared by F-Droid and Play. |

## How the main pieces connect

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

Mental model:

- **The engine is the single logcat owner.** UI and services consume its
  SharedFlow. Never spawn `logcat` anywhere else.
- Crash persistence, surrounding-log capture and notification live in
  `CrashRecorder` (same package as `CrashPolicy`). The engine only decides
  *that* a crash happened and hands over a buffer snapshot; keep it that way
  so the engine file stays about the process.
- `ui/stream/StreamScreen.kt` owns state and gestures; the pure chrome
  (top bars, banners, FABs, selection bar, saved sheet) lives in
  `StreamChrome.kt`. New presentational pieces go there.
- `ui/stream/LogScrollRail.kt` is the right-edge rail: a severity
  fingerprint of the whole buffer, a draggable viewport thumb, and the
  `Line N of M` readout. Its geometry is pure (`ScrollRailMath`) and unit
  tested, so drag↔index mapping changes must keep those tests green. The
  rail is the only thing allowed to be under 48dp wide — it sits on the
  list's edge, where a wider touch band would eat scroll gestures.
- The engine reconnects with backoff when the process dies. A Shizuku or
  root grant change restarts it when that change switches the active path.
  Auto mode tries Shizuku, then root, then the ADB grant, so an existing
  Shizuku setup does not open a root shell.
- Recordings honor the *full* active filter (level/query/tag/exclude/regex +
  package) so what gets recorded matches what the user sees.
- The SDK runs in a different process entirely — it may only read its host
  app's own logs and must never assume LogSleuth (the viewer) is installed.

## Conventions

- All user-facing strings live in `res/values/strings.xml` **and**
  `res/values-zh-rCN/strings.xml`, with identical key sets and matching
  positional format args. Enforced in CI:
  `python3 scripts/check_string_parity.py`.
- Log parsing/filtering/detection logic is pure Kotlin and unit-tested; keep it
  free of Android framework dependencies where possible.
- Engine/VM state flows publish in batches on hot paths (per 64 lines), never
  per item.
- Edge-to-edge insets: the root Scaffold pads the NavHost *and*
  `consumeWindowInsets`s the same values. A screen that adds its own
  `imePadding()` (only the stream search does) therefore gets the keyboard
  remainder, not the whole keyboard — stacking it on the raw Scaffold padding
  lifts the content twice and leaves a blank band above the IME.

## Platform constraints (do not "fix" these)

- Reading other apps' logs takes one of three grants: Shizuku, root
  (libsu, **viewer app only**, opt-in — do not exec `su` until the user
  turns it on), or `adb shell pm grant <pkg> android.permission.READ_LOGS`.
  On API 33+, do not start or reconnect an app-uid logcat while LogSleuth
  is in the background: a decline is cached for about 60 seconds and the
  approval dialog never appears. Shizuku and root skip that check.
  The SDK reads only the host app's own logs and must not depend on libsu.
- PID→package mapping beyond the logcat `uid` column needs a shell
  (Shizuku or root). The uid column itself is available on those two paths.
- Both long-running services declare `foregroundServiceType="specialUse"`, not
  `dataSync`: Android 15 caps `dataSync` at six hours per 24 for apps targeting
  35+, which a crash watcher can never stay under (#118). Use
  `startForegroundOrFalse` — `startForeground` throws when the system refuses
  the start, and letting that escape `onStartCommand` crashes the process and
  turns a sticky restart into a loop. A refusal is recorded in
  `watchBlockedAt`, which `WatchRecovery` turns into a retry cooldown.
- SDK stays permission-free and network-free by design. Root support lives
  in the viewer only.

## Workflow

- Do not commit `local.properties` or IDE/cache junk. The release
  keystore is the exception: `app/release.keystore` and
  `keystore.properties` stay in git so `assembleRelease` signs without a
  password prompt. Do not delete or regenerate them.
- Do not create commits, push, open PRs, or file Issues unless the user asks to
  deliver / ship / push / open a PR (or equivalent).
- Version pins at the time of writing: Gradle 8.11.1, AGP 8.9.3, Kotlin 2.2.21,
  compileSdk 36, targetSdk 36, minSdk 26. Do not bump dependency versions
  without checking the whole build still passes.

### Releasing

Tags are the only release trigger — `.github/workflows/release.yml` runs on
`v*`, re-runs the string gate and unit tests, then `assembleRelease` (signed by
the committed keystore) and publishes the APK to the GitHub Release the in-app
updater reads.

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts` in a PR.
2. Merge it.
3. Tag the merge commit `vX.Y.Z` and push the tag. The workflow fails fast if
   the tag does not equal `versionName`, so a stale build cannot ship under a
   newer version.

### Delivery (Issue + PR + CI)

Default target: [shiaho777/LogSleuth](https://github.com/shiaho777/LogSleuth).
**Every intentional change goes through the loop — no direct pushes to `main`
for normal work.** Human-facing wording of the same loop lives in
[CONTRIBUTING.md](CONTRIBUTING.md); keep both in sync when this changes.

**Language (required):** GitHub **Issues and PRs must be written in English** —
titles, bodies, labels text you author, and delivery comments on the
Issue/PR. Local chat with the user may be Chinese or any language; do not copy
that language into Issue/PR text.

When the user asks to deliver a change, run the loop end-to-end:

1. **Issue first** — reuse an open Issue if one already tracks the work.
2. **Branch** from up-to-date `main` (`codex/` prefix unless told otherwise).
3. **PR into `main`** — body includes `Fixes #N` (or `Closes #N`), what/why,
   and test notes. Use the PR template.
4. **Comment on the Issue** with the PR URL and status.
5. **CI is the merge gate** — the required check is the `build` job in
   `.github/workflows/ci.yml`. Do not merge red; fix and push instead.
   CI never closes Issues.
6. **Merge when green**, confirm the Issue auto-closed; if it did not, close
   it pointing at the merged PR.
7. If merge permission is missing: leave the PR open, comment on the Issue,
   and hand off to a maintainer.

User overrides (skip Issue, direct push, ignore red CI) win for that turn
only — state the override in the PR/Issue comment.
