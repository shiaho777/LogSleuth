# UI/UX polish pass — design

Status: approved by user (approach A, single PR). 2026-09-14.

## Goal

One comprehensive polish pass over the LogSleuth viewer app: motion
continuity, stock-component upgrades, a small set of logic/perf fixes, and a
few tightly-scoped UX additions. No new screens, no navigation restructure,
no dependency bumps. Delivered as one Issue + one `codex/` PR per AGENTS.md.

## Current state (verified)

- material3 `1.5.0-alpha17`, `MaterialExpressiveTheme` + `MotionScheme.expressive()`
  already in `Theme.kt`; dynamic color on S+.
- Nav2 (`navigation-compose 2.9.7`) + `ShortNavigationBar`, 5 top-level tabs,
  transitions are plain `fadeIn`/`fadeOut`.
- `animateItem()` already present on Sessions and Crashes lists; missing on
  the live stream list (deliberately kept off — see below) and session detail.
- `enableEdgeToEdge()` already called; manifest lacks
  `android:enableOnBackInvokedCallback` (predictive back off).
- 134 strings per locale, parity gated by `scripts/check_string_parity.py`.

## Design

### 1. Motion & continuity

- **Nav transitions** (`NavGraph.kt`): `slideIntoContainer`/`slideOutOfContainer`
  + fade for forward, matching pops. Add `android:enableOnBackInvokedCallback="true"`
  to the manifest so NavHost's Animator-based transitions support predictive
  back on Android 13+.
- **Session card → detail shared element**: wrap `NavHost` in
  `SharedTransitionLayout`; expose the scope through a private
  `CompositionLocal`; `sharedBounds` on `SessionCard` keyed by session id,
  matching `sharedBounds` on the detail top bar area via
  `LocalNavAnimatedContentScope`. Fallback if the Nav2 wiring fights:
  drop the shared element, keep the slide transitions.
- **SessionDetail tabs → `HorizontalPager`**: three tabs (logs / crashes /
  bookmarks) become swipeable pages, `pagerState` drives `SecondaryTabRow`.
- **FilterBar expand**: `if (expanded)` → `AnimatedVisibility(expandVertically() +
  fadeIn())`; expand chevron rotates via `animateFloatAsState` (state-driven,
  no gesture → spring spec is correct here).
- **Scroll-to-bottom FAB**: `fadeIn/fadeOut` → `scaleIn/scaleOut` (spring).
- **EngineStatusChip**: STARTING dot pulses via `rememberInfiniteTransition`
  alpha (respects system animator scale automatically).
- **`animateItem()`** added to session-detail log list and crashes tab list.
  Deliberately NOT on the live stream list: lines append at the bottom while
  auto-follow scrolls to bottom; per-item entrance animation would fight the
  follow scroll and add draw cost during bursts.

### 2. Stock-component upgrades

- `CircularProgressIndicator` (session detail loading) → `LoadingIndicator`
  (Expressive morphing polygon; present in alpha17 — verify at compile time,
  fall back to `CircularWavyProgressIndicator` if moved).
- Settings theme `FilterChip`×3 → `SingleChoiceSegmentedButtonRow`.
- Copy feedback: `Toast` → `Snackbar` (LogRow long-press + detail sheet copy;
  snackbar host lifted to where the action originates).
- Sessions + Crashes row deletion → `SwipeToDismissBox` + `Snackbar` undo.
  Fixes the current instant-delete footgun. Undo restores the row by
  deferring the actual DAO delete until the snackbar resolves.
- App-picker rows (FilterBar dialog, Report step 1) → `ListItem` structure
  (correct touch targets and semantics; visuals unchanged).

### 3. Logic / performance fixes (`StreamViewModel`, `LogRow`)

- **`trimLocked` O(n²) → O(1)**: `visible` preserves `all`'s order, so the
  trimmed prefix of `all` maps to a prefix of `visible` — drop while
  `visible.first().seq <= lastRemovedSeq` instead of `indexOfFirst` per item.
- **Search**: debounce ~250ms + `collectLatest`-style cancellation so each
  keystroke doesn't rescan 20k entries; while searching, recompute hits when
  `entries` grows so hit count doesn't go stale.
- **`LogRow.highlighted()`**: called twice per row every recomposition →
  `remember(text, query)`. `compactTimeFormat` (`SimpleDateFormat`, not
  thread-safe) → per-row `remember`ed instance (or `DateTimeFormatter`-free
  manual format — keep it simple, a remembered `SimpleDateFormat`).
- **Search top bar**: `FocusRequester` + `LaunchedEffect` so the keyboard is
  already up when the field appears (today the user must tap the field).

### 4. Small features (user-approved scope)

- **Log text size setting** (`compact / default / comfortable`) in Settings →
  `SettingsRepository` new key → `LogRow` font-size mapping. All strings
  bilingual.
- Crash events swipe-to-delete with undo (listed above).
- **Bookmarks tab rows** (session detail): bare `Text` rows → `ListItem`
  with a bookmark icon — matches the rest of the app's list anatomy.
- Judgment-call allowance: further single-file polish items discovered while
  implementing (e.g. a missed `animateItem`, an unlabeled icon button, a
  hard-coded color that should be a scheme token) get folded in if they are
  strictly small; anything larger gets listed in the PR body as an extra.

### Explicit non-goals

- No Navigation3 migration, no `NavigationSuiteScaffold`, no tablet work.
- No material3 version bump (alpha17 stays; verify APIs against it).
- No changes to `sdk/` (it must stay permission-free/minimal) or `sample/`.
- No `animateItem()` on the live stream (justified above).
- No restructure of `StreamViewModel`'s batching — 120ms staging is correct;
  only the trim/search internals change.

## Testing & verification

- `./gradlew :app:assembleDebug :app:testDebugUnitTest` — existing unit tests
  must stay green; add/adjust tests for `trimLocked`-equivalent logic if it
  lands in a testable pure-Kotlin spot.
- `python3 scripts/check_string_parity.py` — new strings added to both
  `values/` and `values-zh-rCN/`.
- Emulator render pass on the API31 AVD: build, install, screenshot Stream /
  Sessions / SessionDetail / Settings / Setup; light + dark; exercise tab
  swipe and card→detail transition. Logcat checked for frame-skip warnings.
- Manual scroll flick test for list smoothness; predictive back sanity check.

## Delivery

Issue (English) → `codex/ui-ux-polish` branch → PR into `main` with
`Fixes #N` → `build` CI job green → merge → Issue auto-close. The spec file
itself rides the feature branch (no direct commits to `main`).
