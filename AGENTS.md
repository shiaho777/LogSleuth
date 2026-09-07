# AGENTS.md — notes for AI coding agents

## What this is

LogSleuth: a root-free Android logcat viewer app plus an embeddable
zero-permission logging SDK. See README.md / README.zh-CN.md.

## Modules

- `:app` — viewer app. Kotlin, Jetpack Compose (Material 3), Hilt, Room,
  DataStore, Shizuku API (`dev.rikka.shizuku`).
- `:sdk` — `logsleuth-sdk`, the embeddable library. **Must stay permission-free
  and network-free.** Keep its dependency footprint minimal.
- `:sample` — demo app consuming `:sdk`; also used for manual verification.

## Build & verify

```bash
./gradlew assembleDebug
./gradlew test          # JVM unit tests (parser, filters, crash detector)
./gradlew lint
```

- Gradle wrapper: 8.11.1. AGP 8.7.3, Kotlin 2.0.21, compileSdk 35, minSdk 26.
- Do not bump dependency versions without checking the whole build still passes.

## Conventions

- All user-facing strings live in `res/values/strings.xml` **and**
  `res/values-zh-rCN/strings.xml` — always update both.
- Log parsing/filtering/detection logic is pure Kotlin and unit-tested; keep it
  free of Android framework dependencies where possible.
- `core/logcat/LogcatEngine` is the single owner of the logcat process; UI and
  services consume its SharedFlow. Don't spawn logcat processes elsewhere.

## Platform constraints (do not "fix" these)

- Reading other apps' logs requires Shizuku or `adb shell pm grant ... READ_LOGS`.
  There is no third way. The SDK mode reads only the host app's own logs.
- PID→package mapping (per-app filter) is only possible via Shizuku.
