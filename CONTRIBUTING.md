# Contributing to LogSleuth

Thanks for helping. Pick a lane below and ignore the rest — LogSleuth moves
fastest when contributions stay small and well-scoped.

> **English** · [简体中文](#贡献-logsleuth中文)

## Lanes

| You want to… | Go to | Effort |
| --- | --- | --- |
| Report a bug or request a feature | [GitHub Issues](https://github.com/shiaho777/LogSleuth/issues) | minutes |
| Integrate `logsleuth-sdk` into your app | `sample/` + the SDK section of the [README](README.md) | hours |
| Fix a bug or build a feature | This guide ↓ | days |

Not sure which lane fits? Open an issue first. There is no need to write code
before there's agreement on the shape of the change.

## Code contributions

The canonical working guide for AI coding agents and deep contributors is
[AGENTS.md](AGENTS.md) — it documents how the logcat engine, recording, and SDK
connect, plus the delivery rules below in agent-oriented form. Read it before
any non-trivial change to the engine, SDK, or CI.

### Before you write code

- Search [issues](https://github.com/shiaho777/LogSleuth/issues) for prior
  discussion of the same idea.
- For non-trivial changes, open an issue first describing the problem and your
  approach — much cheaper than rewriting after review.
- Avoid adding new dependencies. The SDK must stay permission-free and
  network-free; the app's dependency list is intentionally restrained.

### Local setup

You'll need JDK 17+ and an Android SDK with platform 36 installed.

```bash
git clone https://github.com/shiaho777/LogSleuth.git
cd LogSleuth
./gradlew assembleDebug   # app + sdk + sample
```

Run the checks before submitting (CI runs exactly these):

```bash
python3 scripts/check_string_parity.py   # bilingual string gate
./gradlew test                           # parser / filter / detector tests
./gradlew lint
./gradlew assembleDebug
```

### Where things live

| Area | Path |
| --- | --- |
| Logcat engine, sources, access detection | `app/src/main/java/…/core/logcat/` |
| Filters (model + compiled matcher) | `app/src/main/java/…/core/filter/` |
| Crash / ANR detector | `app/src/main/java/…/core/detect/` |
| Export (txt/zip) & import | `app/src/main/java/…/core/export/`, `…/core/importer/` |
| Shizuku integration | `app/src/main/java/…/core/shizuku/` |
| Root (libsu, viewer only) | `app/src/main/java/…/core/root/` |
| Recording (foreground service, manager, tile, bubble) | `app/src/main/java/…/service/` |
| Compose UI (stream, report wizard, sessions, crashes, settings, setup) | `app/src/main/java/…/ui/` |
| Room entities / DAOs / DataStore settings | `app/src/main/java/…/data/` |
| Embedded SDK (`logsleuth-sdk`) | `sdk/src/main/java/…/sdk/` |
| SDK demo app | `sample/` |

### Submitting

1. Branch from `main` — `fix/…`, `feat/…`, `docs/…`, `chore/…`.
2. Make the change; add strings in **both** `values/strings.xml` and
   `values-zh-rCN/strings.xml` (the parity gate enforces this).
3. Push and open a PR into `main` using the template — include `Fixes #N` for
   the issue the change belongs to.
4. Wait for CI (the `build` job). Do not merge red; fix and push to the same
   branch instead of opening a second PR.
5. The issue closes automatically when the PR merges.

Please write issues and PRs in English so the widest audience can follow
along; conversational Chinese is fine in review comments.

When you point at an issue or a pull request — in chat, in a commit message or
in a PR body — give the full URL (`https://github.com/shiaho777/LogSleuth/issues/N`
or `…/pull/N`) and put the status in its own sentence. A bare number with a
status word glued to it is not a link, and it breaks the GitHub search box.

### Releasing (maintainers)

Releases are cut from tags, not from `main` directly:

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts` via a normal
   PR, and add `docs/releases/vX.Y.Z.md` — the notes the Release page will
   show. `python3 scripts/draft_release_notes.py` prints a skeleton with the
   merged pull requests since the last tag; rewrite each bullet as what the
   user gets, and repeat the whole thing in Chinese after the `<!-- zh-CN -->`
   marker. The format is documented in
   [docs/releases/README.md](docs/releases/README.md).
2. Tag the merged commit `vX.Y.Z` and push the tag.
3. `.github/workflows/release.yml` runs the string gate, the unit tests and
   `assembleRelease` (signed with the keystore committed in this repo), then
   attaches `LogSleuth-X.Y.Z.apk` to the GitHub Release that the in-app updater
   downloads, with the committed notes as the release body. The run stops
   before building if the tag does not match `versionName`, or if the notes
   file is missing.


---

## 贡献 LogSleuth（中文）

选好车道再看其他内容——贡献保持小而聚焦,LogSleuth 才能走得快。

| 你想… | 去哪 | 花费 |
| --- | --- | --- |
| 报 Bug / 提功能建议 | [GitHub Issues](https://github.com/shiaho777/LogSleuth/issues) | 几分钟 |
| 把 `logsleuth-sdk` 集成进你的 App | `sample/` 目录 + [README](README.zh-CN.md) 的 SDK 部分 | 几小时 |
| 修 Bug / 开发功能 | 本指南 ↑ | 几天 |

提交前在本地跑与 CI 相同的检查:

```bash
python3 scripts/check_string_parity.py   # 双语字符串奇偶校验
./gradlew test                           # 解析器 / 过滤器 / 侦测器测试
./gradlew lint
./gradlew assembleDebug
```

要求:

- 所有改动走 **Issue → 分支 → PR → CI 门禁 → 合并** 流程,PR 标题和正文用英文
  (评审讨论里中文没问题),PR 描述里带上 `Fixes #N`
- 分支名用 `fix/…`、`feat/…`、`docs/…`、`chore/…` 这类普通名字,不要带工具或
  智能体的前缀
- 提到 Issue 或 PR 时给完整链接
  (`https://github.com/shiaho777/LogSleuth/issues/N`),状态另起一句话写
- 用户可见字符串必须同时加进 `values/strings.xml` 和 `values-zh-rCN/strings.xml`
  (CI 门禁会强制校验)
- 尽量不加新依赖;SDK 模块必须保持零权限、零网络

### 发版(维护者)

版本由 tag 触发,不直接从 `main` 发布:

1. 在普通 PR 里改 `app/build.gradle.kts` 的 `versionCode` 和 `versionName`,
   同时新增 `docs/releases/vX.Y.Z.md` —— Release 页面要显示的说明。
   `python3 scripts/draft_release_notes.py` 会生成骨架并列出上一个 tag 之后
   合并的 PR;每一条都改写成「用户能拿到什么」,并在 `<!-- zh-CN -->` 之后
   重复一遍中文。格式见 [docs/releases/README.md](docs/releases/README.md)。
2. 合并后给该提交打 `vX.Y.Z` 标签并推送。
3. `.github/workflows/release.yml` 会跑字符串门禁、单测和 `assembleRelease`
   (用仓库内的 keystore 签名),然后把 `LogSleuth-X.Y.Z.apk` 挂到 GitHub
   Release 上,应用内更新读的就是它;发布说明直接用仓库里那份文件。标签与
   `versionName` 不一致、或者说明文件缺失时,都会在构建前直接失败。
