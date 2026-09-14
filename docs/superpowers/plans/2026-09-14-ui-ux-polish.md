# UI/UX Polish Pass Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** One comprehensive polish pass over the LogSleuth viewer app —
motion continuity, stock-component upgrades, logic/perf fixes, and a few
small UX additions — delivered as one `codex/` PR per AGENTS.md.

**Architecture:** All changes are local to `app/` (Compose UI +
`StreamViewModel` internals). No new screens, no dependency changes, no
`sdk/`/`sample/` edits. Spec: `docs/superpowers/specs/2026-09-14-ui-ux-polish-design.md`.

**Tech Stack:** Kotlin 2.2.21, AGP 8.9.3, Compose BOM 2026.06.01,
material3 1.5.0-alpha17 (Expressive — do NOT bump), navigation-compose 2.9.7,
Hilt, Room, DataStore.

## Global Constraints

- Every new user-facing string goes in BOTH `app/src/main/res/values/strings.xml`
  AND `app/src/main/res/values-zh-rCN/strings.xml` with identical keys and
  matching positional format args. Gate: `python3 scripts/check_string_parity.py`.
- material3 stays at `1.5.0-alpha17`. If a referenced Expressive API does not
  compile there, fall back per the task's note — do not bump the version.
- Live-stream `LazyColumn` gets NO `animateItem()` — appends-at-bottom +
  follow-scroll would fight the animation (deliberate, per spec).
- Commits happen on branch `codex/ui-ux-polish`, never directly on `main`.
- Commit message trailer (required by repo convention):
  `Generated with [Devin](https://devin.ai)` +
  `Co-Authored-By: Devin <158243242+devin-ai-integration[bot]@users.noreply.github.com>`
- Verify after each task: `./gradlew :app:compileDebugKotlin` compiles clean.

---

### Task 1: Predictive back + slide nav transitions

**Files:**
- Modify: `app/src/main/AndroidManifest.xml` (application element)
- Modify: `app/src/main/java/io/github/shiaho777/logsleuth/app/ui/navigation/NavGraph.kt:104-113`

**Interfaces:**
- Produces: nothing consumed by later tasks. `LocalSharedTransitionScope`
  arrives in Task 2.

- [ ] **Step 1: Manifest — enable predictive back**

In `<application ...>` add:

```xml
android:enableOnBackInvokedCallback="true"
```

- [ ] **Step 2: NavHost slide+fade transitions**

In `NavGraph.kt`, replace the four transition lambdas on `NavHost` with:

```kotlin
enterTransition = {
    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start) + fadeIn()
},
exitTransition = {
    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start) + fadeOut()
},
popEnterTransition = {
    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End) + fadeIn()
},
popExitTransition = {
    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End) + fadeOut()
},
```

Add imports: `androidx.compose.animation.AnimatedContentTransitionScope`,
`androidx.compose.animation.slideIntoContainer`,
`androidx.compose.animation.slideOutOfContainer` (fadeIn/fadeOut already imported).

- [ ] **Step 3: Compile**

Run: `./gradlew :app:compileDebugKotlin` — Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit** `feat(ui): slide transitions + predictive back`

---

### Task 2: Shared-element session card → detail

**Files:**
- Modify: `app/src/main/java/io/github/shiaho777/logsleuth/app/ui/navigation/NavGraph.kt`
- Modify: `app/src/main/java/io/github/shiaho777/logsleuth/app/ui/sessions/SessionsScreen.kt` (`SessionCard`)
- Modify: `app/src/main/java/io/github/shiaho777/logsleuth/app/ui/sessiondetail/SessionDetailScreen.kt`

**Interfaces:**
- Produces: `val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }`
  declared top-level in `NavGraph.kt` (package `...ui.navigation`). Tasks' screens read it.

- [ ] **Step 1: Wrap NavHost in SharedTransitionLayout**

In `NavGraph.kt`:

```kotlin
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.compositionLocalOf
import androidx.navigation.compose.LocalNavAnimatedContentScope

val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
```

Inside the `Scaffold` content lambda, wrap the `NavHost`:

```kotlin
SharedTransitionLayout {
    CompositionLocalProvider(LocalSharedTransitionScope provides this) {
        NavHost(/* existing args unchanged */) { /* destinations unchanged */ }
    }
}
```

If `SharedTransitionLayout`/`SharedTransitionScope` do not resolve on this
animation version, stop and skip the whole task (spec fallback: keep slide
transitions only — note it in the PR body).

- [ ] **Step 2: SessionCard sharedBounds**

In `SessionsScreen.kt` `SessionCard`, add at the top of the composable:

```kotlin
val sharedScope = LocalSharedTransitionScope.current
val navScope = LocalNavAnimatedContentScope.current
```

then change the `Card` modifier to:

```kotlin
modifier = modifier
    .fillMaxWidth()
    .then(
        if (sharedScope != null && navScope != null) {
            with(sharedScope) {
                Modifier.sharedBounds(
                    sharedContentState = rememberSharedContentState("session-${session.id}"),
                    animatedVisibilityScope = navScope,
                )
            }
        } else Modifier,
    ),
```

Imports: `io.github.shiaho777.logsleuth.app.ui.navigation.LocalSharedTransitionScope`,
`androidx.compose.animation.SharedTransitionScope` (for `rememberSharedContentState` via scope receiver — it is `sharedScope.rememberSharedContentState(...)` inside `with`),
`androidx.navigation.compose.LocalNavAnimatedContentScope`.

Note: `rememberSharedContentState` and `sharedBounds` are members of
`SharedTransitionScope` — call them inside `with(sharedScope) { ... }`.

- [ ] **Step 3: Detail-side matching sharedBounds**

In `SessionDetailScreen`, the nav arg is already available via the VM's
`SavedStateHandle`; expose the id cheaply by reading it in the NavGraph
destination and passing it down:

```kotlin
composable(
    Routes.SESSION_DETAIL,
    arguments = listOf(navArgument("sessionId") { type = NavType.LongType }),
) { entry ->
    SessionDetailScreen(
        sessionId = entry.arguments?.getLong("sessionId") ?: -1L,
        onBack = { navController.popBackStack() },
    )
}
```

Change `SessionDetailScreen` signature to `fun SessionDetailScreen(sessionId: Long, onBack: () -> Unit, viewModel: ... )`. Inside, at the top:

```kotlin
val sharedScope = LocalSharedTransitionScope.current
val navScope = LocalNavAnimatedContentScope.current
```

and on the root `Column` inside the Scaffold content:

```kotlin
Column(
    Modifier
        .fillMaxSize()
        .padding(padding)
        .then(
            if (sharedScope != null && navScope != null) {
                with(sharedScope) {
                    Modifier.sharedBounds(
                        sharedContentState = rememberSharedContentState("session-$sessionId"),
                        animatedVisibilityScope = navScope,
                    )
                }
            } else Modifier,
        ),
) { /* existing */ }
```

- [ ] **Step 4: Compile + commit**

`./gradlew :app:compileDebugKotlin`; commit `feat(ui): shared-element container transform sessions→detail`.

---

### Task 3: SessionDetail — swipeable tabs + list polish

**Files:**
- Modify: `app/src/main/java/io/github/shiaho777/logsleuth/app/ui/sessiondetail/SessionDetailScreen.kt`

**Interfaces:**
- Consumes: Task 2's `sessionId` parameter on `SessionDetailScreen`.
- Produces: none.

- [ ] **Step 1: Replace `when(tab)` with HorizontalPager**

Imports: `androidx.compose.foundation.pager.HorizontalPager`,
`androidx.compose.foundation.pager.rememberPagerState`,
`androidx.compose.runtime.rememberCoroutineScope`, `kotlinx.coroutines.launch`,
`androidx.compose.material3.LoadingIndicator` (Expressive; if unresolved on
alpha17 use `CircularWavyProgressIndicator`, else keep
`CircularProgressIndicator`).

Replace `var tab by remember { mutableIntStateOf(0) }` with:

```kotlin
val pagerState = rememberPagerState(pageCount = { 3 })
val scope = rememberCoroutineScope()
```

`SecondaryTabRow(selectedTabIndex = pagerState.currentPage)`; each `Tab`
`onClick = { scope.launch { pagerState.animateScrollToPage(N) } }`.

Replace the `when (tab) { ... }` block with:

```kotlin
HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
    when (page) {
        0 -> Column { /* existing FilterBar + list/loading/empty branch */ }
        1 -> /* existing crashes LazyColumn */
        else -> /* existing bookmarks LazyColumn */
    }
}
```

- [ ] **Step 2: LoadingIndicator + animateItem + bookmark ListItem**

- `ui.loading` branch: `CircularProgressIndicator` → `LoadingIndicator(Modifier.align(Alignment.CenterHorizontally))` (the `Column` wraps it in a `Box(Modifier.fillMaxSize())` so `align` works — adjust parent to `Box` if needed).
- Both inner `LazyColumn`s: add `key = { ui.crashes[it].id }` / `key = { ui.bookmarks[it].id }` and `Modifier.animateItem()` on items.
- Bookmarks tab row: bare `Text` → `ListItem` with `leadingContent = { Icon(Icons.Default.Bookmark, null) }`, `headlineContent = { Text(time + note) }`. Import `androidx.compose.material3.ListItem`, `androidx.compose.material.icons.filled.Bookmark`.

- [ ] **Step 3: Compile + commit** `feat(ui): swipeable session detail tabs`.

---

### Task 4: StreamScreen motion polish + search autofocus

**Files:**
- Modify: `app/src/main/java/io/github/shiaho777/logsleuth/app/ui/stream/StreamScreen.kt`
- Modify: `app/src/main/java/io/github/shiaho777/logsleuth/app/ui/components/FilterBar.kt`

- [ ] **Step 1: FAB fade → scale**

In `StreamScreen` FAB `AnimatedVisibility`: `enter = scaleIn()`, `exit = scaleOut()` (spring defaults; imports `androidx.compose.animation.scaleIn/scaleOut`).

- [ ] **Step 2: EngineStatusChip STARTING pulse**

Inside `EngineStatusChip`, when `state == STARTING` animate the dot:

```kotlin
val dotAlpha by if (state == LogcatEngine.State.STARTING) {
    rememberInfiniteTransition(label = "engine").animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "engineDot",
    )
} else {
    remember { mutableStateOf(1f) }
}
```

Apply `.background(color.copy(alpha = dotAlpha))` on the dot `Box`. Imports:
`rememberInfiniteTransition`, `animateFloat`, `infiniteRepeatable`, `tween`,
`RepeatMode`, `mutableStateOf`.

- [ ] **Step 3: FilterBar expand animation + chevron**

In `FilterBar.kt`: wrap the expanded `Column` in

```kotlin
AnimatedVisibility(
    visible = expanded,
    enter = expandVertically() + fadeIn(),
    exit = shrinkVertically() + fadeOut(),
) { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { /* existing fields */ } }
```

Chevron: replace the two-icon `if` with one icon rotated:

```kotlin
val chevron by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
Icon(Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.rotate(chevron))
```

Imports: `androidx.compose.animation.AnimatedVisibility`, `expandVertically`,
`shrinkVertically`, `fadeIn`, `fadeOut`, `animateFloatAsState`,
`androidx.compose.ui.draw.rotate`, `androidx.compose.ui.Modifier`.

- [ ] **Step 4: Search bar autofocus**

In `SearchTopBar` (`StreamScreen.kt`), give the `TextField`:

```kotlin
val focusRequester = remember { FocusRequester() }
LaunchedEffect(Unit) { focusRequester.requestFocus() }
// TextField(modifier = Modifier.fillMaxWidth().focusRequester(focusRequester), ...)
```

Imports: `androidx.compose.ui.focus.FocusRequester`, `focusRequester`,
`androidx.compose.runtime.LaunchedEffect` (already imported).

- [ ] **Step 5: Compile + commit** `feat(ui): stream screen motion polish`.

---

### Task 5: StreamViewModel — O(1) trim + search debounce/live hits

**Files:**
- Modify: `app/src/main/java/io/github/shiaho777/logsleuth/app/ui/stream/StreamViewModel.kt`
- Test: `app/src/test/java/io/github/shiaho777/logsleuth/app/ui/stream/EvictOverflowTest.kt` (new)

**Interfaces:**
- Produces: `internal fun evictOverflow(all: MutableList<UiLogEntry>, visible: MutableList<UiLogEntry>, cap: Int)` top-level in `StreamViewModel.kt`.

- [ ] **Step 1: Write the failing test**

```kotlin
package io.github.shiaho777.logsleuth.app.ui.stream

import io.github.shiaho777.logsleuth.app.core.logcat.LogLevel
import io.github.shiaho777.logsleuth.app.core.logcat.LogcatEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class EvictOverflowTest {

    private fun entry(seq: Long) = UiLogEntry(
        seq,
        LogcatEntry(0L, 1, 1, null, LogLevel.I, "t", "m", "raw"),
    )

    @Test
    fun `evicted prefix drops matching prefix of visible`() {
        // all: seq 0..9, visible: only even seqs
        val all = (0L..9L).map(::entry).toMutableList()
        val visible = (0L..9L step 2).map(::entry).toMutableList()
        evictOverflow(all, visible, cap = 5)
        assertEquals((5L..9L).toList(), all.map { it.seq })
        assertEquals(listOf(6L, 8L), visible.map { it.seq })
    }

    @Test
    fun `no overflow leaves both lists untouched`() {
        val all = (0L..3L).map(::entry).toMutableList()
        val visible = all.toMutableList()
        evictOverflow(all, visible, cap = 5)
        assertEquals(4, all.size)
        assertEquals(4, visible.size)
    }
}
```

Run `./gradlew :app:testDebugUnitTest --tests '*EvictOverflowTest*'` — FAIL (unresolved reference).

- [ ] **Step 2: Implement `evictOverflow` + rewire `trimLocked`**

Top-level in `StreamViewModel.kt` (file bottom, `internal`):

```kotlin
/**
 * Drops the oldest `all.size - cap` entries. `visible` is a seq-ordered
 * subsequence of `all`, so evicted entries form a prefix of it — O(overflow)
 * instead of the old per-entry indexOfFirst scan.
 */
internal fun evictOverflow(
    all: MutableList<UiLogEntry>,
    visible: MutableList<UiLogEntry>,
    cap: Int,
) {
    val overflow = all.size - cap
    if (overflow <= 0) return
    val lastEvictedSeq = all[overflow - 1].seq
    repeat(overflow) { all.removeAt(0) }
    var drop = 0
    while (drop < visible.size && visible[drop].seq <= lastEvictedSeq) drop++
    repeat(drop) { visible.removeAt(0) }
}
```

Replace the body of `trimLocked()` with `evictOverflow(all, visible, bufferCap)`.

- [ ] **Step 3: Run test — expect PASS.**

- [ ] **Step 4: Search debounce + live hit recompute**

Add field `private var searchJob: Job? = null` (import `kotlinx.coroutines.Job`).

Rewrite `setSearchQuery`:

```kotlin
fun setSearchQuery(query: String) {
    _ui.update { it.copy(searchQuery = query) }
    searchJob?.cancel()
    searchJob = viewModelScope.launch(Dispatchers.Default) {
        delay(250)
        recomputeHits(query)
    }
}
```

Extract the scan into:

```kotlin
private suspend fun recomputeHits(query: String) {
    val hits = if (query.isBlank()) {
        emptyList()
    } else {
        listMutex.withLock {
            visible.mapIndexedNotNull { index, uiEntry ->
                val e = uiEntry.entry
                if (e.tag.contains(query, true) || e.message.contains(query, true)) index else null
            }
        }
    }
    _ui.update {
        it.copy(
            searchHits = hits,
            searchHitIndex = if (hits.isEmpty()) -1 else it.searchHitIndex.coerceIn(0, hits.lastIndex),
        )
    }
}
```

In `drainStaged`, after `publishLocked()` runs (inside the same
`!pausedNow && addedVisible > 0` path, after releasing `listMutex`), refresh
stale hits while the search bar is open:

```kotlin
if (!pausedNow && addedVisible > 0) {
    val s = _ui.value
    if (s.searching && s.searchQuery.isNotBlank()) recomputeHits(s.searchQuery)
}
```

(Place this after the `listMutex.withLock { ... }` block; `recomputeHits`
re-acquires the mutex.)

- [ ] **Step 5: Compile + test + commit** `fix(stream): O(1) buffer trim, debounced live search hits`.

---

### Task 6: LogRow — cached highlight/format, snackbar, text-size param

**Files:**
- Modify: `app/src/main/java/io/github/shiaho777/logsleuth/app/ui/components/LogRow.kt`
- Modify: `app/src/main/java/io/github/shiaho777/logsleuth/app/ui/theme/Theme.kt` (add `LocalLogTextScale`)
- Modify: `app/src/main/java/io/github/shiaho777/logsleuth/app/MainActivity.kt` (provide the local)
- Modify: `app/src/main/java/io/github/shiaho777/logsleuth/app/ui/stream/StreamScreen.kt` + `SessionDetailScreen.kt` (snackbar hosts)

**Interfaces:**
- Produces: `val LocalLogTextScale = compositionLocalOf { 1f }` in `Theme.kt` (package `...ui.theme`). `LogRow` reads it; Task 7's settings write it via `SettingsRepository.setLogTextScale(index: Int)` where index 0=compact(0.85f), 1=default(1f), 2=comfortable(1.15f).

- [ ] **Step 1: Theme local**

In `Theme.kt` add:

```kotlin
val LocalLogTextScale = compositionLocalOf { 1f }
```

(import `androidx.compose.runtime.compositionLocalOf`).

- [ ] **Step 2: MainActivity provides it**

In `MainActivity.onCreate`'s `setContent`, wrap `Surface` (or inside theme):

```kotlin
val logScale = when (settings?.logTextScale ?: 1) { 0 -> 0.85f; 2 -> 1.15f; else -> 1f }
CompositionLocalProvider(LocalLogTextScale provides logScale) {
    Surface(...) { ... }
}
```

Imports: `androidx.compose.runtime.CompositionLocalProvider`,
`io.github.shiaho777.logsleuth.app.ui.theme.LocalLogTextScale`.
(`settings.logTextScale` lands in Task 7 — add a `val Settings.logTextScale: Int` field default 1 and repo key NOW in this task to keep compilable order: add field + `Keys.LOG_TEXT_SCALE` + `setLogTextScale` to `SettingsRepository` in this step.)

- [ ] **Step 3: LogRow — remember highlight + time + scale**

In `LogRow`:

```kotlin
val scale = LocalLogTextScale.current
val timeText = remember(entry.timestampMillis) {
    SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(entry.timestampMillis))
}
val tagText = remember(entry.tag, highlight) { highlighted(entry.tag, highlight) }
val msgText = remember(entry.message, highlight) { highlighted(entry.message, highlight) }
```

Delete the top-level `compactTimeFormat`. Apply `* scale` to the two `fontSize`/`lineHeight` values in `LogMetaStyle`/`LogMessageStyle` — simplest: make them functions of scale:

```kotlin
@Composable private fun logMetaStyle(scale: Float) = MaterialTheme.typography.bodySmall.copy(
    fontFamily = FontFamily.Monospace, fontSize = 10.5.sp * scale, lineHeight = 13.sp * scale,
)
@Composable private fun logMessageStyle(scale: Float) = MaterialTheme.typography.bodySmall.copy(
    fontFamily = FontFamily.Monospace, fontSize = 11.5.sp * scale, lineHeight = 15.sp * scale,
)
```

- [ ] **Step 4: Copy feedback Toast → Snackbar**

`LogRow` gets a new optional param `snackbar: SnackbarHostState? = null`. Long-press and sheet copy callbacks do:

```kotlin
scope.launch { snackbar?.showSnackbar(copiedMessage) }  // scope = rememberCoroutineScope()
```

fall back to Toast only when `snackbar == null` (keeps `LogRow` usable in
previews/sheets without a host). Add `SnackbarHostState`+`SnackbarHost` to
`SessionDetailScreen`'s Scaffold (StreamScreen already has one) and pass the
host into `LogRow` at both call sites.

- [ ] **Step 5: Compile + commit** `feat(ui): cached log row rendering, snackbar copy, scalable log text`.

---

### Task 7: Settings — segmented theme picker + log text size

**Files:**
- Modify: `app/src/main/java/io/github/shiaho777/logsleuth/app/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/io/github/shiaho777/logsleuth/app/ui/settings/SettingsViewModel.kt`
- Modify: both `strings.xml` (new keys below)

- [ ] **Step 1: Strings (both locales, same order)**

values/strings.xml:

```xml
<string name="settings_log_text_size">Log text size</string>
<string name="text_size_compact">Compact</string>
<string name="text_size_default">Default</string>
<string name="text_size_comfortable">Comfortable</string>
```

values-zh-rCN/strings.xml:

```xml
<string name="settings_log_text_size">日志字号</string>
<string name="text_size_compact">紧凑</string>
<string name="text_size_default">默认</string>
<string name="text_size_comfortable">舒适</string>
```

- [ ] **Step 2: ViewModel setter**

```kotlin
fun setLogTextScale(value: Int) = viewModelScope.launch {
    settingsRepository.setLogTextScale(value)
}
```

- [ ] **Step 3: Theme picker → segmented buttons**

Replace the three `FilterChip`s in the theme Card with:

```kotlin
SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
    listOf("system" to R.string.theme_system, "light" to R.string.theme_light, "dark" to R.string.theme_dark)
        .forEachIndexed { i, (value, label) ->
            SegmentedButton(
                selected = s.theme == value,
                onClick = { viewModel.setTheme(value) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = 3),
            ) { Text(stringResource(label)) }
        }
}
```

Imports: `SingleChoiceSegmentedButtonRow`, `SegmentedButton`, `SegmentedButtonDefaults`; drop `FilterChip` import.

- [ ] **Step 4: Log text size card**

New Card after the theme card, same segmented pattern over
`listOf(0 to R.string.text_size_compact, 1 to R.string.text_size_default, 2 to R.string.text_size_comfortable)`
calling `viewModel.setLogTextScale(value)`.

- [ ] **Step 5: Parity + compile + commit** `feat(settings): segmented pickers, log text size option`.

Run `python3 scripts/check_string_parity.py`.

---

### Task 8: Swipe-to-delete + undo (Sessions + Crashes)

**Files:**
- Modify: `app/src/main/java/io/github/shiaho777/logsleuth/app/ui/sessions/SessionsScreen.kt`
- Modify: `app/src/main/java/io/github/shiaho777/logsleuth/app/ui/sessions/SessionsViewModel.kt`
- Modify: `app/src/main/java/io/github/shiaho777/logsleuth/app/ui/crashes/CrashesScreen.kt`
- Modify: `app/src/main/java/io/github/shiaho777/logsleuth/app/ui/crashes/CrashesViewModel.kt`
- Modify: both `strings.xml` (`undo`, `session_deleted`, `crash_deleted`)

**Interfaces:**
- Produces (SessionsViewModel): `fun requestDelete(session: SessionEntity)` /
  `fun undoDelete()` / `val pendingDelete: StateFlow<SessionEntity?>`.
- Produces (CrashesViewModel): `fun requestDelete(id: Long)` / `fun undoDelete()` /
  `val pendingDeleteId: StateFlow<Long?>`.

- [ ] **Step 1: Strings**

```xml
<string name="undo">Undo</string>
<string name="session_deleted">Session deleted</string>
<string name="crash_deleted">Crash event deleted</string>
```

zh-rCN: `撤销` / `会话已删除` / `崩溃事件已删除`.

- [ ] **Step 2: Deferred-delete in both ViewModels**

SessionsViewModel:

```kotlin
private val _pendingDelete = MutableStateFlow<SessionEntity?>(null)
val pendingDelete: StateFlow<SessionEntity?> = _pendingDelete.asStateFlow()
private var deleteJob: Job? = null

fun requestDelete(session: SessionEntity) {
    deleteJob?.cancel()
    _pendingDelete.value = session
    deleteJob = viewModelScope.launch {
        delay(4_000)
        _pendingDelete.value = null
        delete(session)   // existing real delete
    }
}

fun undoDelete() {
    deleteJob?.cancel()
    _pendingDelete.value = null
}
```

UI list filters out `pendingDelete?.id` so the row vanishes instantly while
the file/row deletion waits out the undo window. Same shape for
CrashesViewModel with `CrashEventEntity`/`pendingDeleteId` calling existing
`delete(id)`. Imports: `MutableStateFlow`, `asStateFlow`, `Job`, `delay`.

- [ ] **Step 3: SwipeToDismissBox rows**

Sessions list item:

```kotlin
val dismissState = rememberSwipeToDismissBoxState(
    confirmValueChange = { it == SwipeToDismissBoxValue.EndToStart },
)
SwipeToDismissBox(
    state = dismissState,
    backgroundContent = {
        Box(Modifier.fillMaxSize().padding(end = 20.dp), contentAlignment = Alignment.CenterEnd) {
            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
        }
    },
    modifier = Modifier.animateItem(),
    enableDismissFromStartToEnd = false,
) {
    SessionCard(session, onOpen, onShare, onDelete = { viewModel.requestDelete(session) })
}
LaunchedEffect(dismissState.currentValue) {
    if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
        viewModel.requestDelete(session)
        dismissState.snapTo(SwipeToDismissBoxValue.Settled)
    }
}
```

Watch the snackbar: collect `pendingDelete`; while non-null show
`snackbar.showSnackbar(session_deleted, actionLabel = undo)` → on
`ActionPerformed` call `viewModel.undoDelete()`. Keep the trash `IconButton`
on the card wired to the same `requestDelete` (both paths get undo).

Same pattern on `CrashesScreen` (`crash_deleted` / `undoDelete()`). Imports:
`SwipeToDismissBox`, `SwipeToDismissBoxValue`, `rememberSwipeToDismissBoxState`,
`SnackbarResult`, `LaunchedEffect`, `snapTo`.

- [ ] **Step 4: Parity + compile + commit** `feat(lists): swipe-to-delete with undo for sessions and crashes`.

---

### Task 9: App pickers → ListItem

**Files:**
- Modify: `app/src/main/java/io/github/shiaho777/logsleuth/app/ui/components/FilterBar.kt` (`AppPickerDialog`)
- Modify: `app/src/main/java/io/github/shiaho777/logsleuth/app/ui/report/ReportScreen.kt` (`StepPickApp` rows)

- [ ] **Step 1:** In `AppPickerDialog`, replace `TextButton` rows with `ListItem(headlineContent = { Text("${app.label}  ·  ${app.packageName}") }, modifier = Modifier.clickable { onSelect(...) })`; keep selected-name tint. In `StepPickApp`, restyle the `Surface` row content into `ListItem` anatomy (leading check icon, two-line text) — keep the existing selected container color.

- [ ] **Step 2: Compile + commit** `refactor(ui): ListItem anatomy in app pickers`.

---

### Task 10: Full verification + delivery

- [ ] `./gradlew :app:assembleDebug :app:testDebugUnitTest` — all green.
- [ ] `python3 scripts/check_string_parity.py` — clean.
- [ ] Boot API31 emulator, install debug APK, screenshot Stream / Sessions /
  SessionDetail (all 3 tabs) / Settings / Setup in light + dark; exercise
  card→detail transition and swipe-to-delete; check `adb logcat` for
  `Skipped frames`/`Davey!` during a fast scroll.
- [ ] Push branch, open PR `Fixes #N` (English, PR template), CI `build`
  green → merge → confirm Issue auto-closed.

## Self-review notes

- Spec coverage: all spec items map to Tasks 1–9; verification/delivery = Task 10.
- Type consistency: `LocalSharedTransitionScope` (Task 2) consumed by Tasks 2–3 screens;
  `LocalLogTextScale` (Task 6) produced in Theme.kt, provided in MainActivity,
  consumed by LogRow; `Settings.logTextScale` field + repo setter added in
  Task 6 Step 2, used by Task 7. `evictOverflow` signature matches Task 5 test.
- Deliberate exclusions (spec): no `animateItem` on live stream, no dep bumps.
