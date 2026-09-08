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
| `docs/screenshots/` | README screenshot set (currently branded placeholders). |
| `fastlane/metadata/` | Store listing copy (en-US + zh-CN), shared by F-Droid and Play. |

## How the main pieces connect

```text
LogcatSource (local / Shizuku)
  → LogcatEngine (singleton, owns THE logcat process)
      SharedFlow<LogcatEntry> ─┬→ StreamViewModel (120ms batches) → UI list
                                ├→ RecordingManager → session file + Room row
                                └→ CrashDetector → crash_events + notification

logsleuth-sdk (inside a host app, no permissions):
  Sleuth.init → LogcatCaptureThread (logcat --pid=self)
              + CrashHandler (chained UncaughtExceptionHandler)
              + AnrWatchdog (main-looper heartbeat)
                → LogFileStore (ring files, redaction) → LogSharer (zip share)
```

Mental model:

- **The engine is the single logcat owner.** UI and services consume its
  SharedFlow. Never spawn `logcat` anywhere else.
- The engine reconnects with backoff when the process dies; Shizuku grant
  changes restart it immediately.
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

## Platform constraints (do not "fix" these)

- Reading other apps' logs requires Shizuku or
  `adb shell pm grant <pkg> android.permission.READ_LOGS`. There is no third
  way. The SDK mode reads only the host app's own logs.
- PID→package mapping beyond the logcat `uid` column is only possible via
  shell (Shizuku).
- SDK stays permission-free and network-free by design.

## Workflow

- Do not commit secrets, `local.properties`, keystores, or IDE/cache junk.
- Do not create commits, push, open PRs, or file Issues unless the user asks to
  deliver / ship / push / open a PR (or equivalent).
- Version pins at the time of writing: Gradle 8.11.1, AGP 8.9.3, Kotlin 2.2.21,
  compileSdk 36, minSdk 26. Do not bump dependency versions without checking
  the whole build still passes.

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
