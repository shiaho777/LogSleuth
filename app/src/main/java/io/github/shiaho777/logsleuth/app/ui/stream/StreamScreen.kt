package io.github.shiaho777.logsleuth.app.ui.stream

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
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
import io.github.shiaho777.logsleuth.app.core.logcat.LogcatEngine
import io.github.shiaho777.logsleuth.app.ui.components.EmptyState
import io.github.shiaho777.logsleuth.app.ui.components.FilterBar
import io.github.shiaho777.logsleuth.app.ui.guide.LocalTourController
import io.github.shiaho777.logsleuth.app.ui.guide.tourTarget
import io.github.shiaho777.logsleuth.app.ui.components.LogRow
import io.github.shiaho777.logsleuth.app.ui.components.LogScopeDialog
import io.github.shiaho777.logsleuth.app.ui.components.NoAccessState
import io.github.shiaho777.logsleuth.app.ui.navigation.Routes
import io.github.shiaho777.logsleuth.app.ui.theme.LevelColors
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
                onMakeFilter = { viewModel.saveFilterFromLine(it) },
                onOpenSetup = { onNavigate(Routes.SETUP) },
                onDisableRules = viewModel::disableEnabledRules,
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

/** Slim banner shown while a recording is active. */
@Composable
private fun RecordingBanner(
    lineCount: Long,
    paused: Boolean,
    onPause: () -> Unit,
    onStop: () -> Unit,
) {
    Surface(
        color = if (paused) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        },
        contentColor = if (paused) {
            MaterialTheme.colorScheme.onTertiaryContainer
        } else {
            MaterialTheme.colorScheme.onErrorContainer
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (paused) {
                Icon(
                    Icons.Default.Pause,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
            } else {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error),
                )
            }
            Spacer(Modifier.size(10.dp))
            Text(
                text = stringResource(
                    if (paused) R.string.recording_banner_paused else R.string.recording_banner,
                    lineCount,
                ),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onPause) {
                Icon(
                    if (paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.size(4.dp))
                Text(stringResource(if (paused) R.string.resume else R.string.pause))
            }
            TextButton(onClick = onStop) {
                Icon(
                    Icons.Default.Stop,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.size(4.dp))
                Text(stringResource(R.string.recording_banner_stop))
            }
        }
    }
}

/** Small pill showing the engine state at the top-right of the list. */
@Composable
private fun EngineStatusChip(
    state: LogcatEngine.State,
    adbHeld: Boolean,
    modifier: Modifier = Modifier,
) {
    val (label, color) = when {
        adbHeld -> stringResource(R.string.engine_adb_waiting) to LevelColors.W
        state == LogcatEngine.State.RUNNING -> stringResource(R.string.engine_streaming) to LevelColors.I
        state == LogcatEngine.State.STARTING -> stringResource(R.string.engine_connecting) to LevelColors.W
        state == LogcatEngine.State.STOPPED -> stringResource(R.string.engine_stopped) to LevelColors.V
        else -> stringResource(R.string.engine_error) to LevelColors.E
    }
    val dotAlpha by if (state == LogcatEngine.State.STARTING) {
        rememberInfiniteTransition(label = "engine").animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "engineDot",
        )
    } else {
        remember { mutableStateOf(1f) }
    }
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        shape = MaterialTheme.shapes.small,
        shadowElevation = 2.dp,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = dotAlpha)),
            )
            Spacer(Modifier.size(5.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StreamTopBar(
    paused: Boolean,
    bubbleEnabled: Boolean,
    onPauseToggle: () -> Unit,
    onSearch: () -> Unit,
    onBubbleToggle: () -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        actions = {
            IconButton(onClick = onPauseToggle, modifier = Modifier.tourTarget("pause")) {
                Icon(
                    if (paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = stringResource(if (paused) R.string.resume else R.string.pause),
                    tint = if (paused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onSearch, modifier = Modifier.tourTarget("search")) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = stringResource(R.string.search_hint),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onBubbleToggle, modifier = Modifier.tourTarget("bubble")) {
                Icon(
                    Icons.Default.BubbleChart,
                    contentDescription = stringResource(R.string.bubble_controls),
                    tint = if (bubbleEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Row(Modifier.tourTarget("scopeActions")) {
                IconButton(onClick = onClear) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = stringResource(R.string.clear),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onSave) {
                    Icon(
                        Icons.Default.Save,
                        contentDescription = stringResource(R.string.save_to_session),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(
    query: String,
    hits: Int,
    hitIndex: Int,
    caseSensitive: Boolean,
    onQueryChange: (String) -> Unit,
    onCaseSensitiveChange: (Boolean) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    TopAppBar(
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
        },
        actions = {
            Text(
                if (hits == 0) "0/0" else "${hitIndex + 1}/$hits",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            IconButton(onClick = { onCaseSensitiveChange(!caseSensitive) }) {
                Icon(
                    Icons.Default.TextFields,
                    contentDescription = stringResource(R.string.search_case_sensitive),
                    tint = if (caseSensitive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            IconButton(onClick = onPrev, enabled = hits > 0) {
                Icon(
                    Icons.Default.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.search_prev),
                )
            }
            IconButton(onClick = onNext, enabled = hits > 0) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.search_next),
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
            }
        },
    )
}

@Composable
private fun JumpFabs(
    listState: LazyListState,
    entries: List<UiLogEntry>,
    followTail: Boolean,
    selecting: Boolean,
    onJumpTop: () -> Unit,
    onJumpBottom: () -> Unit,
) {
    val canScrollBack by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > 0
        }
    }
    val hasEntries = entries.isNotEmpty()
    Column(
        modifier = Modifier.tourTarget("fab"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AnimatedVisibility(
            visible = canScrollBack && hasEntries && !selecting,
            enter = scaleIn(),
            exit = scaleOut(),
        ) {
            FloatingActionButton(
                onClick = onJumpTop,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Icon(
                    Icons.Default.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.scroll_top),
                )
            }
        }
        AnimatedVisibility(
            visible = !followTail && hasEntries && !selecting,
            enter = scaleIn(),
            exit = scaleOut(),
        ) {
            FloatingActionButton(
                onClick = onJumpBottom,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.scroll_bottom),
                )
            }
        }
    }
}

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
    onExtendedCopy: (String) -> Unit,
    onSaveSelection: (List<io.github.shiaho777.logsleuth.app.core.logcat.LogcatEntry>) -> Unit,
    onCopy: (String) -> Unit,
    onMakeFilter: (io.github.shiaho777.logsleuth.app.core.logcat.LogcatEntry) -> Unit,
    onOpenSetup: () -> Unit,
    onDisableRules: () -> Unit,
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
                action = if (ui.filteredOut && rulesActive) {
                    {
                        TextButton(onClick = onDisableRules) {
                            Text(stringResource(R.string.filter_rules_clear))
                        }
                    }
                } else {
                    null
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
                .tourTarget("engineStatus"),
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

/** Floating action bar shown while a range selection is active. */
@Composable
private fun SelectionBar(
    visible: Boolean,
    count: Int,
    onSelectAll: () -> Unit,
    onExtendedCopy: () -> Unit,
    onSave: () -> Unit,
    onCopy: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically { it / 2 } + fadeIn(),
        exit = slideOutVertically { it / 2 } + fadeOut(),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 18.dp, end = 4.dp),
            ) {
                Text(
                    text = stringResource(R.string.selected_count, count),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.size(8.dp))
                IconButton(onClick = onSelectAll) {
                    Icon(
                        Icons.Default.SelectAll,
                        contentDescription = stringResource(R.string.select_all),
                    )
                }
                IconButton(onClick = onExtendedCopy) {
                    Icon(
                        Icons.Default.TextFields,
                        contentDescription = stringResource(R.string.extended_copy),
                    )
                }
                TextButton(onClick = onSave) {
                    Icon(
                        Icons.Default.Save,
                        contentDescription = stringResource(R.string.selection_save),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.selection_save))
                }
                TextButton(onClick = onCopy) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.copy),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.copy))
                }
                IconButton(onClick = onClear) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.close),
                    )
                }
            }
        }
    }
}


/** Slim banner shown while the stream is paused. */
@Composable
private fun PausedBanner(incoming: Int, onResume: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Pause,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.size(10.dp))
            Text(
                text = stringResource(R.string.paused_banner, incoming),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onResume) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.size(4.dp))
                Text(stringResource(R.string.paused_resume))
            }
        }
    }
}

/** Bottom sheet shown right after a recording stops — one-tap share. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordingSavedSheet(
    session: io.github.shiaho777.logsleuth.app.data.db.SessionEntity,
    backfill: Long,
    isSnapshot: Boolean = false,
    onShare: (io.github.shiaho777.logsleuth.app.core.export.SessionExporter.Format) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Default.FiberManualRecord,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(
                    if (isSnapshot) R.string.snapshot_saved else R.string.recording_saved,
                ),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                if (backfill > 0) {
                    stringResource(
                        R.string.recording_saved_lines_context,
                        session.lineCount,
                        backfill,
                    )
                } else {
                    stringResource(R.string.recording_saved_lines, session.lineCount)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.material3.Button(
                    onClick = { onShare(io.github.shiaho777.logsleuth.app.core.export.SessionExporter.Format.ZIP) },
                    modifier = Modifier.weight(1f),
                ) { Text("ZIP") }
                androidx.compose.material3.OutlinedButton(
                    onClick = { onShare(io.github.shiaho777.logsleuth.app.core.export.SessionExporter.Format.TXT) },
                    modifier = Modifier.weight(1f),
                ) { Text("TXT") }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.later))
            }
        }
    }
}
