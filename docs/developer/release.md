# Releasing

Releases are cut from tags. The tag is the only trigger: nothing is
published from `main` directly, and the build that ships is the one the
workflow builds from the tagged commit.

## The runbook

1. **Collect the changes.** The release notes are the merged pull requests
   since the last tag, rewritten as what the user gets:

   ```bash
   python3 scripts/draft_release_notes.py --list   # the merged PRs
   python3 scripts/draft_release_notes.py           # a skeleton, version filled in
   ```

2. **Open the version-bump PR.** It bumps `versionCode` and `versionName` in
   `app/build.gradle.kts` and adds `docs/releases/vX.Y.Z.md` — the bilingual
   notes. The format is documented in
   [docs/releases/README.md](https://github.com/shiaho777/LogSleuth/blob/main/docs/releases/README.md).

3. **Write the notes.** Sections are **Added / Improved / Fixed**, one bullet
   each, saying what the user gets rather than what the diff did, and ending
   with the pull request it came from: `(#124)`, or `(#124, closes #121)`.
   Repeat the whole thing in Chinese after a `<!-- zh-CN -->` marker.

4. **Merge, then tag.**

   ```bash
   git checkout main && git pull --ff-only
   git tag -a vX.Y.Z -m "LogSleuth X.Y.Z"
   git push origin vX.Y.Z
   ```

5. **Watch the workflow.** `.github/workflows/release.yml` runs the string
   gate and unit tests, builds the sample against the staged Maven SDK, and
   verifies the signed APK against the configured certificate. It publishes
   `LogSleuth-X.Y.Z.APK`, `logsleuth-sdk-X.Y.Z-maven.zip`, and a SHA-256
   manifest with the committed notes. The docs workflow then deploys the SDK
   version to the public Maven repository.

   The asset name is `<app>-<version>.APK`, the same shape the mature
   `web-to-app` releases use. The in-app updater matches the asset by its
   `.apk` extension, case-insensitively, so the extension case does not
   matter to it.

6. **Publish.** The Release page is what the in-app updater reads. The store
   listings live in `fastlane/metadata/`.

### What a release body looks like

The body is `docs/releases/vX.Y.Z.md`, verbatim. It is bilingual — English
first, then the same text again in Chinese after a `<!-- zh-CN -->` marker —
and grouped into **Added / Improved / Fixed**, which are the three headings
the reader is looking for:

```markdown
**LogSleuth 0.4.0 — one line on what changed.**

`versionName` 0.4.0, `versionCode` 11.

Requires Android 8.0 (API 26)+.

## Added

- **What the user gets** — the behaviour, not the diff. (#124)

<!-- zh-CN -->

**LogSleuth 0.4.0 —— 中文一句话。**

## 新增

- **用户能拿到什么** —— 写行为，不写 diff。(#124)
```

## Release signing

Before tagging, configure the GitHub Actions environment named `release` for
release tags. Set `LOGSLEUTH_RELEASE_KEYSTORE_BASE64`,
`LOGSLEUTH_RELEASE_STORE_PASSWORD`, `LOGSLEUTH_RELEASE_KEY_ALIAS` and
`LOGSLEUTH_RELEASE_KEY_PASSWORD` as environment secrets. Set the public
SHA-256 signing-certificate fingerprint as the environment variable
`LOGSLEUTH_RELEASE_CERT_SHA256`. The workflow verifies the signed APK before
publishing.

Treat the historic key in Git as exposed. Record the signing certificate for
each distribution channel and test the upgrade from its last release before
selecting a replacement. Android 9 and newer can use a v3 signing lineage for
APK rotation. Android 8.x cannot switch to the new signer through that lineage.
For these devices, decide how existing installations will migrate and how users
can preserve sessions before changing what the release serves. Google Play
signing has its own key upgrade workflow.

See the [Android APK signing reference](https://developer.android.com/tools/apksigner)
and [Play signing guide](https://developer.android.com/studio/publish/app-signing).

## SDK smoke build

```bash
./gradlew :sdk:publishReleasePublicationToStagingRepository -Plogsleuth.sdkVersion=0.0.0-local
./gradlew :sample:assembleDebug -Plogsleuth.sdkVersion=0.0.0-local
```

Install the sample, export its log ZIP, and open that ZIP in the viewer to
verify replay. The Maven archive contains the AAR, POM, Gradle metadata and
sources for the release version.

## What the workflow refuses to do

| Guard | Why |
| --- | --- |
| The tag must equal `versionName` | Otherwise the APK would carry an older version number under a newer release page |
| `docs/releases/<tag>.md` must exist | A release cannot ship an unreviewed, English-only changelog |
| The string parity gate runs first | A bilingual app cannot ship with one language behind |
| The unit tests run first | The tag is not a test of last resort |

## Version numbers

`versionCode` must increase — the in-app updater compares it. `versionName`
is what the user sees, and it is the tag without the `v`.

## Checklist

- [ ] `python3 scripts/check_string_parity.py`
- [ ] `./gradlew test lint assembleDebug`
- [ ] Version bumped, and the notes file written in both languages
- [ ] Tag pushed, workflow green, APK attached to the Release
- [ ] The in-app updater sees the new `versionCode`
