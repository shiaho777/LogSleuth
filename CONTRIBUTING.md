# Contributing to LogSleuth

Thanks for your interest in contributing!

## Ways to contribute

- **Bug reports**: open an issue with reproduction steps, device model, Android
  version, and whether you used Shizuku or the ADB grant.
- **Feature requests**: open an issue describing the use case first.
- **Pull requests**: fork, branch from `main`, keep changes focused, and make
  sure CI is green.

## Development setup

1. JDK 17+ and Android SDK 35.
2. `./gradlew assembleDebug` to build, `./gradlew test` for unit tests.
3. The project uses Kotlin + Jetpack Compose (Material 3), Hilt, Room, and
   DataStore. Please match the existing code style (official Kotlin style).

## Pull request checklist

- [ ] Unit tests pass (`./gradlew test`)
- [ ] Lint passes (`./gradlew lint`)
- [ ] Strings added to both `values/strings.xml` and `values-zh-rCN/strings.xml`
- [ ] Public behavior changes are reflected in the READMEs

## Code of conduct

Be kind, be constructive, assume good intent.
