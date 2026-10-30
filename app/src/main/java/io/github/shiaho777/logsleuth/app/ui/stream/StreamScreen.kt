package io.github.shiaho777.logsleuth.app.ui.stream

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.shiaho777.logsleuth.app.R
import io.github.shiaho777.logsleuth.app.core.logcat.formatVisible
import io.github.shiaho777.logsleuth.app.ui.components.LocalAppIconCache
import io.github.shiaho777.logsleuth.app.ui.components.LocalLogRowPrefs
import io.github.shiaho777.logsleuth.app.core.apps.AppLogGroup
import io.github.shiaho777.logsleuth.app.core.filter.LogFilter
import io.github.shiaho777.logsleuth.app.core.logcat.LogcatEngine
import io.github.shiaho777.logsleuth.app.ui.components.EmptyState
import io.github.shiaho777.logsleuth.app.ui.components.FilterBar
import io.github.shiaho777.logsleuth.app.ui.guide.LocalTourController
import io.github.shiaho777.logsleuth.app.ui.guide.tourTarget
import io.github.shiaho777.logsleuth.app.ui.filters.FilterEditorDialog
import io.github.shiaho777.logsleuth.app.ui.components.LogRow
import io.github.shiaho777.logsleuth.app.ui.components.LogScopeDialog
import io.github.shiaho777.logsleuth.app.ui.components.NoAccessState
import io.github.shiaho777.logsleuth.app.ui.navigation.Routes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamScreen(
    onNavigate: (String) -> Unit,
    viewModel: StreamViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    val context = androidx.compose.ui.platform.LocalContext.current

    // Tail-follow pin — standard chat-log semantics:
    //   · at the newest line, new logs stick to the bottom of the screen;
    //   · any move toward older lines releases it. The rows on screen then
    //     stay put — including across ring-buffer trims, which delete a
    //     prefix and would otherwise slide that same index onto newer lines;
    //   · scrolling back to the end, or the ↓ FAB, follows again.
    // A finger delta releases before layout catches up. The scroll position
    // is the backstop: head eviction does not move firstVisibleItemIndex,
    // so it cannot look like a user pull.
    var followTail by remember { mutableStateOf(true) }
    var touching by remember { mutableStateOf(false) }
    var pullAccum by remember { mutableFloatStateOf(0f) }
    var parkedSeq by remember { mutableLongStateOf(-1L) }
    var parkedOffset by remember { mutableIntStateOf(0) }
    val touchSlop = LocalViewConfiguration.current.touchSlop

    // Range selection (long-press + drag): seq bounds of the selected span.
    var selAnchor by remember { mutableStateOf(-1L) }
    var selEnd by remember { mutableStateOf(-1L) }
    var extendedCopyText by remember { mutableStateOf<String?>(null) }
    // "Create a filter from this line" opens the editor prefilled, so the
    // user can drop fields before saving instead of getting a fixed rule.
    var filterDraft by remember { mutableStateOf<LogFilter?>(null) }
    val selecting = selAnchor >= 0L
    val selectingNow by rememberUpdatedState(selecting)

    fun parkAt(index: Int, offset: Int) {
        viewModel.entries.getOrNull(index)?.let { parkedSeq = it.seq }
        parkedOffset = offset
        followTail = false
    }

    LaunchedEffect(listState) {
        var prevIdx = listState.firstVisibleItemIndex
        var prevOff = listState.firstVisibleItemScrollOffset
        snapshotFlow {
            Triple(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
                touching,
            )
        }.collect { (idx, off, fingerDown) ->
            val towardOlder = idx < prevIdx || (idx == prevIdx && off < prevOff)
            if (fingerDown && towardOlder && !selectingNow) parkAt(idx, off)
            if (!followTail) {
                viewModel.entries.getOrNull(idx)?.let { parkedSeq = it.seq }
                parkedOffset = off
            }
            prevIdx = idx
            prevOff = off
        }
    }

    // Settled on the newest line: follow again. Re-arming while the last
    // couple of rows were merely still visible used to pull the reader
    // back into the stream.
    LaunchedEffect(listState) {
        snapshotFlow {
            !touching &&
                !listState.isScrollInProgress &&
                !listState.canScrollForward &&
                viewModel.entries.isNotEmpty()
        }.collect { atTail ->
            if (atTail && !selectingNow) followTail = true
        }
    }

    val clipboard = LocalClipboardManager.current
    val copiedMsg = stringResource(R.string.copied)
    val filterSavedMsg = stringResource(R.string.filter_saved_from_line)
    LaunchedEffect(ui.filterNotice) {
        if (ui.filterNotice > 0) snackbar.showSnackbar(filterSavedMsg)
    }

    // Clear / save scope picker — one shared dialog, two actions.
    var scopeDialog by remember { mutableStateOf<ScopeAction?>(null) }
    var scopeGroups by remember { mutableStateOf<List<AppLogGroup>?>(null) }
    // Tour demo opens the picker straight into per-app mode.
    var scopeStartPerApp by remember { mutableStateOf(false) }
    // True while the picker on screen was opened by the tour — dismiss or
    // confirm then means "done looking" and moves the beat forward, and
    // confirm never mutates the buffer during the demo. Cleared on every
    // close path so a later user-opened dialog behaves normally.
    var scopeByTour by remember { mutableStateOf(false) }
    LaunchedEffect(scopeDialog) {
        if (scopeDialog != null) scopeGroups = viewModel.appGroups()
    }

    // Coach-mark hooks: the tour overlay fires these to demo features
    // live — pause for real, open the search bar, pop the scope dialog,
    // nudge the list so the jump FABs appear. Each "off" action only
    // undoes what its "on" partner actually did, so pre-existing user
    // state (e.g. already paused) is preserved.
    val tour = LocalTourController.current
    DisposableEffect(Unit) {
        var pausedByTour = false
        var searchByTour = false
        tour.actions["pauseOn"] = {
            if (!viewModel.ui.value.paused) {
                viewModel.setPaused(true)
                pausedByTour = true
            }
        }
        tour.actions["pauseOff"] = {
            if (pausedByTour) viewModel.setPaused(false)
        }
        var selectedByTour = false
        var peekedByTour = false
        tour.actions["searchOpen"] = {
            if (!viewModel.ui.value.searching) {
                viewModel.setSearching(true)
                // Type a real query so hits and the jump buttons light up —
                // "logsleuth" always matches our own lines in the buffer.
                viewModel.setSearchQuery("logsleuth")
                searchByTour = true
            }
        }
        tour.actions["searchClose"] = {
            if (searchByTour) {
                viewModel.setSearchQuery("")
                viewModel.setSearching(false)
            }
        }
        tour.actions["scopeOpenPerApp"] = {
            if (scopeDialog == null) {
                scopeStartPerApp = true
                scopeDialog = ScopeAction.CLEAR
                scopeByTour = true
            }
        }
        tour.actions["scopeClose"] = {
            if (scopeByTour) {
                scopeByTour = false
                scopeDialog = null
                scopeStartPerApp = false
            }
        }
        tour.actions["selectDemo"] = {
            val visible = viewModel.entries
            if (visible.size > 4 && !selecting) {
                val i = (listState.firstVisibleItemIndex + 1)
                    .coerceIn(0, visible.size - 3)
                selAnchor = visible[i].seq
                selEnd = visible[i + 2].seq
                selectedByTour = true
            }
        }
        tour.actions["selectOff"] = {
            if (selectedByTour) {
                selAnchor = -1L
                selEnd = -1L
            }
        }
        tour.actions["fabPeek"] = {
            if (viewModel.entries.isNotEmpty()) {
                followTail = false
                peekedByTour = true
                scope.launch { listState.scroll { scrollBy(600f) } }
            }
        }
        tour.actions["fabBack"] = {
            if (peekedByTour) {
                followTail = true
                scope.launch {
                    listState.scrollToItem(
                        viewModel.entries.lastIndex.coerceAtLeast(0),
                    )
                }
            }
        }
        // Skip at ANY step must undo whatever the tour turned on —
        // per-step leave actions only fire for the step being left.
        tour.actions["leave.stream"] = {
            if (pausedByTour) viewModel.setPaused(false)
            if (searchByTour) {
                viewModel.setSearchQuery("")
                viewModel.setSearching(false)
            }
            if (scopeByTour) {
                scopeByTour = false
                scopeDialog = null
                scopeStartPerApp = false
            }
            if (selectedByTour) {
                selAnchor = -1L
                selEnd = -1L
            }
            if (peekedByTour) followTail = true
        }
        onDispose {
            listOf(
                "pauseOn", "pauseOff", "searchOpen", "searchClose",
                "scopeOpenPerApp", "scopeClose", "selectDemo", "selectOff",
                "fabPeek", "fabBack", "leave.stream",
            ).forEach { tour.actions.remove(it) }
        }
    }

    // Back first dismisses an active selection.
    BackHandler(enabled = selecting) {
        selAnchor = -1L
        selEnd = -1L
    }

    Scaffold(
        topBar = {
            if (ui.searching) {
                Box(Modifier.tourTarget("searchBar")) {
                    SearchTopBar(
                        query = ui.searchQuery,
                        hits = ui.searchHits.size,
                        hitIndex = ui.searchHitIndex,
                        caseSensitive = ui.searchCaseSensitive,
                        onQueryChange = viewModel::setSearchQuery,
                        onCaseSensitiveChange = viewModel::setSearchCaseSensitive,
                        onPrev = { viewModel.nextSearchHit(-1) },
                        onNext = { viewModel.nextSearchHit(1) },
                        onClose = { viewModel.setSearching(false) },
                    )
                }
            } else {
                StreamTopBar(
                    paused = ui.paused,
                    bubbleEnabled = ui.bubbleEnabled,
                    onPauseToggle = {
                        haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                        viewModel.setPaused(!ui.paused)
                    },
                    onSearch = { viewModel.setSearching(true) },
                    onBubbleToggle = {
                        haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                        if (!viewModel.toggleBubble()) {
                            context.startActivity(viewModel.overlaySettingsIntent())
                        }
                    },
                    onClear = { scopeDialog = ScopeAction.CLEAR },
                    onSave = { scopeDialog = ScopeAction.SAVE },
                    onRestart = viewModel::restartLogging,
                )
            }
        },
        floatingActionButton = {
            JumpFabs(
                listState = listState,
                entries = viewModel.entries,
                followTail = followTail,
                selecting = selecting,
                onJumpTop = {
                    parkAt(0, 0)
                    scope.launch { listState.scrollToItem(0) }
                },
                onJumpBottom = { followTail = true },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            // Recording status banner — appears with a slide-down animation
            AnimatedVisibility(
                visible = ui.recording.isRecording,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                RecordingBanner(
                    lineCount = ui.recording.lineCount,
                    paused = ui.recording.paused,
                    onPause = viewModel::toggleRecordingPause,
                    onStop = viewModel::toggleRecording,
                )
            }

            // Paused banner — tells the user new lines are being buffered.
            AnimatedVisibility(
                visible = ui.paused,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Box(Modifier.tourTarget("pausedBanner")) {
                    PausedBanner(
                        incoming = ui.pausedIncoming,
                        onResume = { viewModel.setPaused(false) },
                    )
                }
            }

            Box(Modifier.tourTarget("filterBar")) {
                FilterBar(
                    filter = ui.filter,
                    regexInvalid = ui.regexInvalid,
                    apps = ui.apps,
                    presets = ui.presets,
                    onFilterChange = viewModel::setFilter,
                    onSavePreset = viewModel::savePreset,
                    onApplyPreset = viewModel::applyPreset,
                    onDisableRule = { viewModel.setRuleEnabled(it, false) },
                    onManageFilters = { onNavigate(Routes.FILTERS) },
                )
            }

            StreamListPane(
                ui = ui,
                entries = viewModel.entries,
                listState = listState,
                followTail = followTail,
                touching = touching,
                selecting = selecting,
                parkedSeq = parkedSeq,
                parkedOffset = parkedOffset,
                onParkedSeq = { parkedSeq = it },
                onParkedOffset = { parkedOffset = it },
                selAnchor = selAnchor,
                selEnd = selEnd,
                onSelectStart = { seq -> selAnchor = seq; selEnd = seq },
                onSelectExtend = { seq -> selEnd = seq },
                onSelectAll = { first, last ->
                    selAnchor = first
                    selEnd = last
                },
                onClearSelection = {
                    selAnchor = -1L
                    selEnd = -1L
                },
                onScrubTo = { index ->
                    // Grabbing the rail leaves the tail first, otherwise the pin
                    // would snap the list back to the newest line.
                    parkAt(index, 0)
                    listState.requestScrollToItem(index)
                },
                onExtendedCopy = { extendedCopyText = it },
                onSaveSelection = { selected ->
                    selAnchor = -1L
                    selEnd = -1L
                    if (selected.isNotEmpty()) viewModel.saveSelection(selected)
                },
                onCopy = { text ->
                    selAnchor = -1L
                    selEnd = -1L
                    if (text.isNotEmpty()) {
                        clipboard.setText(AnnotatedString(text))
                        scope.launch { snackbar.showSnackbar(copiedMsg) }
                    }
                },
                onMakeFilter = { filterDraft = viewModel.draftFilterFromLine(it) },
                onOpenSetup = { onNavigate(Routes.SETUP) },
                onDisableRules = viewModel::disableEnabledRules,
                onRestartLogging = viewModel::restartLogging,
                rulesActive = ui.presets.any { it.enabled },
                snackbar = snackbar,
                onUserTouch = { down ->
                    touching = down
                    if (!down) pullAccum = 0f
                },
                onUserDrag = { dy ->
                    // Finger sliding down moves toward older lines.
                    // Upward jitter must not wipe the accumulated
                    // pull, or a real scroll never releases the pin.
                    if (dy > 0f) pullAccum += dy
                    if (pullAccum > touchSlop && !selecting) {
                        pullAccum = 0f
                        parkAt(
                            listState.firstVisibleItemIndex,
                            listState.firstVisibleItemScrollOffset,
                        )
                    }
                },
            )
        }
    }

    // Search-hit jumping. Leave the tail so the pin does not yank back.
    LaunchedEffect(ui.searchHitIndex) {
        val target = ui.searchHits.getOrNull(ui.searchHitIndex) ?: return@LaunchedEffect
        parkAt(target, 0)
        listState.scrollToItem(target)
    }

    // Shared scope picker — clear or save, all or a checked app subset.
    scopeDialog?.let { action ->
        LogScopeDialog(
            title = stringResource(
                if (action == ScopeAction.CLEAR) {
                    R.string.clear_logs_title
                } else {
                    R.string.save_session_title
                },
            ),
            icon = if (action == ScopeAction.CLEAR) {
                Icons.Default.Clear
            } else {
                Icons.Default.Save
            },
            confirmLabel = stringResource(
                if (action == ScopeAction.CLEAR) R.string.clear else R.string.save,
            ),
            confirmTint = if (action == ScopeAction.CLEAR) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
            groups = scopeGroups,
            totalLines = viewModel.entries.size,
            initialPerApp = scopeStartPerApp,
            // Step 10 hides the overlay card, and this Dialog's own window
            // covers the scrim anyway — the only place tour chrome can
            // show is inside the demo dialog itself.
            tourCaption = if (scopeByTour) {
                stringResource(
                    R.string.tour_scope_demo_caption,
                    tour.stepIndex + 1,
                    tour.stepCount,
                )
            } else {
                null
            },
            onDismiss = {
                // A tour-opened demo dialog that the user closes counts
                // as "next step" — the dialog window eats the tap that
                // dismissed it, so without this nudge the beat looks dead.
                if (scopeByTour) tour.fire("nextStep")
                scopeByTour = false
                scopeDialog = null
                scopeStartPerApp = false
            },
            onConfirm = { uids ->
                val tourDemo = scopeByTour
                scopeByTour = false
                if (tourDemo) {
                    // Demo mode: closing the picker advances the tour;
                    // it must never actually wipe or save the buffer.
                    tour.fire("nextStep")
                } else if (action == ScopeAction.CLEAR) {
                    if (uids == null) viewModel.clear() else viewModel.clearApps(uids)
                } else {
                    viewModel.saveToSession(uids)
                }
                scopeDialog = null
                scopeStartPerApp = false
            },
        )
    }

    // Recording finished → offer to share immediately.
    ui.finishedSession?.let { session ->
        RecordingSavedSheet(
            session = session,
            backfill = ui.finishedBackfill,
            isSnapshot = ui.finishedSnapshot,
            onShare = { viewModel.shareFinishedSession(it) },
            onDismiss = viewModel::dismissFinishedSession,
        )
    }

    filterDraft?.let { draft ->
        FilterEditorDialog(
            initial = draft,
            apps = ui.apps,
            onDismiss = { filterDraft = null },
            onSave = { filter ->
                viewModel.saveFilter(filter)
                filterDraft = null
            },
        )
    }

    extendedCopyText?.let { text ->
        AlertDialog(
            onDismissRequest = { extendedCopyText = null },
            title = { Text(stringResource(R.string.extended_copy)) },
            text = {
                SelectionContainer {
                    Text(
                        text,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { extendedCopyText = null }) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    }
}

/** Which top-bar action opened the shared scope picker. */
private enum class ScopeAction { CLEAR, SAVE }

/**
 * The log list and the chrome that has to move with it. Reading [entries]
 * here keeps a new line from recomposing the top bar and the filter bar.
 */
@Composable
private fun StreamListPane(
    ui: StreamUiState,
    entries: List<UiLogEntry>,
    listState: LazyListState,
    followTail: Boolean,
    touching: Boolean,
    selecting: Boolean,
    parkedSeq: Long,
    parkedOffset: Int,
    onParkedSeq: (Long) -> Unit,
    onParkedOffset: (Int) -> Unit,
    selAnchor: Long,
    selEnd: Long,
    onSelectStart: (Long) -> Unit,
    onSelectExtend: (Long) -> Unit,
    onSelectAll: (Long, Long) -> Unit,
    onClearSelection: () -> Unit,
    onScrubTo: (Int) -> Unit,
    onExtendedCopy: (String) -> Unit,
    onSaveSelection: (List<io.github.shiaho777.logsleuth.app.core.logcat.LogcatEntry>) -> Unit,
    onCopy: (String) -> Unit,
    onMakeFilter: (io.github.shiaho777.logsleuth.app.core.logcat.LogcatEntry) -> Unit,
    onOpenSetup: () -> Unit,
    onDisableRules: () -> Unit,
    onRestartLogging: () -> Unit,
    rulesActive: Boolean,
    snackbar: SnackbarHostState,
    onUserTouch: (Boolean) -> Unit,
    onUserDrag: (Float) -> Unit,
) {
    // Pinned tail sticks to the newest line in the same layout pass.
    // While a finger is down the pin lets go, so a pull is not fighting
    // a scroll request. Once the reader has left the tail, keep the parked
    // line on screen: trimming the ring buffer deletes a prefix and the
    // same index would otherwise show newer lines.
    if (!followTail && !touching && !listState.isScrollInProgress && parkedSeq >= 0L) {
        val idx = parkedIndex(entries, parkedSeq)
        if (
            idx >= 0 &&
            (listState.firstVisibleItemIndex != idx ||
                listState.firstVisibleItemScrollOffset != parkedOffset)
        ) {
            SideEffect {
                val kept = entries.getOrNull(idx)?.seq ?: return@SideEffect
                if (kept != parkedSeq) {
                    onParkedSeq(kept)
                    onParkedOffset(0)
                }
                listState.requestScrollToItem(idx, parkedOffset)
            }
        }
    }
    if (followTail && !touching && !ui.paused && !selecting && entries.isNotEmpty()) {
        SideEffect {
            listState.requestScrollToItem(entries.lastIndex)
        }
    }

    val selLo = minOf(selAnchor, selEnd)
    val selHi = maxOf(selAnchor, selEnd)
    val rowPrefs = LocalLogRowPrefs.current
    val iconCache = LocalAppIconCache.current
    Box(Modifier.fillMaxSize()) {
        when {
            ui.access?.granted != true -> NoAccessState(onOpenSetup = onOpenSetup)
            entries.isEmpty() -> EmptyState(
                text = when {
                    ui.adbHeld -> stringResource(R.string.stream_adb_needs_foreground)
                    ui.engineState == LogcatEngine.State.ERROR && ui.lastError != null ->
                        stringResource(R.string.stream_error_detail, ui.lastError!!)
                    ui.engineState == LogcatEngine.State.STARTING ->
                        stringResource(R.string.empty_logs_waiting)
                    ui.filteredOut -> stringResource(R.string.empty_logs_filtered)
                    else -> stringResource(R.string.empty_logs)
                },
                action = when {
                    ui.filteredOut && rulesActive -> {
                        {
                            TextButton(onClick = onDisableRules) {
                                Text(stringResource(R.string.filter_rules_clear))
                            }
                        }
                    }
                    // The engine reconnects on its own; a wedged stream needs
                    // a manual kick that keeps the resume stamp, so the ring
                    // buffer is not dumped a second time.
                    ui.engineState == LogcatEngine.State.ERROR && !ui.adbHeld -> {
                        {
                            TextButton(onClick = onRestartLogging) {
                                Text(stringResource(R.string.restart_logging))
                            }
                        }
                    }
                    else -> null
                },
            )
            else -> LogList(
                entries = entries,
                listState = listState,
                searchQuery = ui.searchQuery,
                currentHit = ui.searchHits.getOrNull(ui.searchHitIndex),
                selAnchor = selAnchor,
                selEnd = selEnd,
                onSelectStart = onSelectStart,
                onSelectExtend = onSelectExtend,
                onMakeFilter = onMakeFilter,
                snackbar = snackbar,
                onUserTouch = onUserTouch,
                onUserDrag = onUserDrag,
            )
        }

        EngineStatusChip(
            state = ui.engineState,
            adbHeld = ui.adbHeld,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                // Clear of the progress rail on the right edge.
                .padding(end = 26.dp)
                .tourTarget("engineStatus"),
        )

        LogScrollRail(
            entries = entries,
            listState = listState,
            onScrubTo = onScrubTo,
            modifier = Modifier.align(Alignment.CenterEnd),
        )

        SelectionBar(
            visible = selecting,
            count = entries.count { it.seq in selLo..selHi },
            onSelectAll = {
                val first = entries.firstOrNull()?.seq ?: return@SelectionBar
                val last = entries.lastOrNull()?.seq ?: return@SelectionBar
                onSelectAll(first, last)
            },
            onExtendedCopy = {
                onExtendedCopy(
                    entries.filter { it.seq in selLo..selHi }.joinToString("\n") {
                        formatVisible(
                            it.entry,
                            rowPrefs.visibleColumns(),
                            appLabel = iconCache?.labelFor(it.entry.uid),
                        )
                    },
                )
            },
            onSave = {
                onSaveSelection(
                    entries.filter { it.seq in selLo..selHi }.map { it.entry },
                )
            },
            onCopy = {
                onCopy(
                    entries.filter { it.seq in selLo..selHi }.joinToString("\n") {
                        formatVisible(
                            it.entry,
                            rowPrefs.visibleColumns(),
                            appLabel = iconCache?.labelFor(it.entry.uid),
                        )
                    },
                )
            },
            onClear = onClearSelection,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
                .tourTarget("selectionBar"),
        )
    }
}

@Composable
private fun LogList(
    entries: List<UiLogEntry>,
    listState: LazyListState,
    searchQuery: String,
    currentHit: Int?,
    snackbar: SnackbarHostState,
    selAnchor: Long,
    selEnd: Long,
    onSelectStart: (Long) -> Unit,
    onSelectExtend: (Long) -> Unit,
    onMakeFilter: (io.github.shiaho777.logsleuth.app.core.logcat.LogcatEntry) -> Unit = {},
    onUserTouch: (Boolean) -> Unit = {},
    onUserDrag: (Float) -> Unit = {},
) {
    val currentEntries by rememberUpdatedState(entries)
    // Read through State: pointerInput keeps the first lambda instance, so
    // selection state must be dereferenced fresh, not captured by value.
    val selectingState by rememberUpdatedState(selAnchor >= 0L)
    val haptic = LocalHapticFeedback.current
    val lo = minOf(selAnchor, selEnd)
    val hi = maxOf(selAnchor, selEnd)
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            // Passive touch tracking for tail-follow release: Initial pass +
            // requireUnconsumed=false, so it never eats events and can't
            // break scrolling or the drag-select gesture below.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onUserTouch(true)
                    while (currentEvent.changes.any { it.pressed }) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        // Report the raw finger delta. Head eviction moves
                        // the list under a held finger and is not a pull.
                        val dy = event.changes
                            .sumOf { (it.position.y - it.previousPosition.y).toDouble() }
                            .toFloat()
                        if (dy != 0f) onUserDrag(dy)
                    }
                    onUserTouch(false)
                }
            }
            .logDragSelect(
                listState = listState,
                isSelecting = { selectingState },
                seqAt = { currentEntries.getOrNull(it)?.seq },
                onSelectExtend = onSelectExtend,
            ),
        // No inter-item spacing: gaps between rows are dead zones for the
        // long-press that starts a selection — every Y must land on a row.
    ) {
        items(
            count = entries.size,
            key = { index -> entries[index].seq },
        ) { index ->
            val uiEntry = entries[index]
            // No per-row placement or fade. The viewport pins to the newest
            // line; a spring on top of that is what opened the empty gap.
            LogRow(
                entry = uiEntry.entry,
                highlight = searchQuery.takeIf { it.isNotBlank() },
                isCurrentHit = currentHit == index,
                isSelected = uiEntry.seq in lo..hi,
                snackbar = snackbar,
                onClick = if (selAnchor >= 0L) {
                    { onSelectExtend(uiEntry.seq) }
                } else {
                    null
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSelectStart(uiEntry.seq)
                },
                onMakeFilter = onMakeFilter,
            )
        }
    }
}
