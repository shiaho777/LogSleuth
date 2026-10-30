# Developer docs

These pages are for people working on LogSleuth itself: contributors, and
coding agents. They explain how the two halves fit together and how to change
them without breaking the other one.

::: tip Authoritative reference
[`AGENTS.md`](https://github.com/shiaho777/LogSleuth/blob/main/AGENTS.md) at
the repository root is the authoritative guide for coding agents. These pages
expand on it with more context.
:::

## Repository layout

| Path | Role |
| --- | --- |
| `app/` | The viewer app: logcat engine, Compose UI, recording, crash detection, settings. |
| `sdk/` | `logsleuth-sdk` — the embeddable logging library. Permission-free, network-free, no libsu. |
| `sample/` | Demo app consuming `:sdk`; used to verify the SDK loop by hand. |
| `scripts/` | Repository gates and release helpers. |
| `docs/` | This documentation site, and the per-release notes in `docs/releases/`. |
| `fastlane/metadata/` | Store listing copy, shared by F-Droid and Play. |

The SDK is a separate Gradle module on purpose: it must stay small enough to
paste into somebody else's app, and it must never gain a dependency that
would change what the host app asks for.

## The three Gradle modules

- **`:app`** — the viewer. `applicationId = io.github.shiaho777.logsleuth`,
  `compileSdk 36`, `targetSdk 36`, `minSdk 26`.
- **`:sdk`** — the embeddable library. It reads only its own host app's logs
  (`logcat --pid=self`), chains an `UncaughtExceptionHandler`, watches the
  main looper for ANRs, and writes redacted ring files.
- **`:sample`** — a host app for `:sdk`. It exists so the SDK loop can be
  checked on a device without building a second test harness.

## The mental model

Two rules explain most of the code:

1. **The engine owns the only `logcat` process.** The UI, the recording
   service and the crash watcher all consume the same
   `SharedFlow<LogcatEntry>`. If you need another source of lines, you almost
   certainly need a new `LogcatSource`, not a second process.
2. **Pure logic lives in `core/`, and it is unit tested.** Parsing, filtering,
   detection, export and the geometry behind the scroll rail have no Android
   dependencies, so they can be tested in milliseconds.

## Where to go next

- [Architecture](/developer/architecture) — the data flow, end to end
- [Change recipes](/developer/recipes) — the everyday changes and their steps
- [Releasing](/developer/release) — the release runbook
