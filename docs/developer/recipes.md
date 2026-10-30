# Change recipes

The everyday changes, and the steps that make them land. Follow a recipe end
to end; stopping halfway is how a feature works on one screen and nowhere
else.

## 1. Add a user-visible string

1. Add the key to `app/src/main/res/values/strings.xml` **and**
   `app/src/main/res/values-zh-rCN/strings.xml`, with matching positional
   format args.
2. Run `python3 scripts/check_string_parity.py`. The gate fails on a missing
   key, a mismatched key set, or a different number of format args.
3. Reference it with `stringResource(R.string.your_key)`.

## 2. Add parsing, filtering or detection logic

1. Put it in `core/<area>/` as pure Kotlin — no Android framework types.
2. Add a unit test under `app/src/test/java/…/core/<area>/`.
3. Keep the ViewModel and the engine thin: they call the pure function and
   publish the result.

`LogFilter.fromLine` is the model for "build a filter out of a log line":
pure, tested, and it returns the model rather than saving anything.

## 3. Change the logcat stream

1. One process, owned by `LogcatEngine`. Never spawn a second `logcat`.
2. Keep the `-T` resume stamp, so a reconnect does not dump the ring again.
3. Keep the API 33+ foreground rule: an app-uid logcat may not start while
   the app is in the background.
4. If you add a buffer, add it to `LogcatSource.buildCommand` and to
   `LogcatCommandTest`.

## 4. Change a foreground service

1. Use `specialUse`, never `dataSync` — Android 15 caps `dataSync` at six
   hours per 24 hours, which a long-lived service cannot stay under.
2. Start it with `startForegroundOrFalse`. A refused start must stop the
   service, never take the process down.
3. Record the refusal (`watchBlockedAt`) so the next attempt is not immediate,
   and say so in the UI instead of claiming the service is alive.

## 5. Change the list UI

1. New presentational pieces go into `StreamChrome.kt`; `StreamScreen.kt`
   keeps state and gestures.
2. Geometry that maps positions to indices goes into a pure object with
   tests — `ScrollRailMath` is the model.
3. Anything the user sees as a number should come from one place, so the
   screen and the rail cannot disagree.

## 6. Change the SDK

1. The SDK stays permission-free, network-free, and free of libsu.
2. It may only read its own host app's logs.
3. Verify with the `:sample` app on a device: `Sleuth.init` → capture →
   crash → share the zip.

## 7. Ship it

See [Releasing](/developer/release) for the version bump, the release notes
and the tag. The delivery loop itself is in
[AGENTS.md](https://github.com/shiaho777/LogSleuth/blob/main/AGENTS.md) and
[CONTRIBUTING.md](https://github.com/shiaho777/LogSleuth/blob/main/CONTRIBUTING.md).
