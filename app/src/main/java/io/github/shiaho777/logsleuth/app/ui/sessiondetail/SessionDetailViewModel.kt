package io.github.shiaho777.logsleuth.app.ui.sessiondetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.shiaho777.logsleuth.app.core.apps.AppChoice
import io.github.shiaho777.logsleuth.app.core.apps.InstalledApps
import io.github.shiaho777.logsleuth.app.core.crashes.CrashContextStore
import io.github.shiaho777.logsleuth.app.core.export.SessionExporter
import io.github.shiaho777.logsleuth.app.core.filter.CompiledFilter
import io.github.shiaho777.logsleuth.app.core.filter.FilterStack
import io.github.shiaho777.logsleuth.app.core.filter.LogFilter
import io.github.shiaho777.logsleuth.app.data.db.toEntity
import io.github.shiaho777.logsleuth.app.data.db.toLogFilter
import io.github.shiaho777.logsleuth.app.core.logcat.EntryAssembler
import io.github.shiaho777.logsleuth.app.data.db.BookmarkDao
import io.github.shiaho777.logsleuth.app.data.db.BookmarkEntity
import io.github.shiaho777.logsleuth.app.data.db.CrashEventDao
import io.github.shiaho777.logsleuth.app.data.db.CrashEventEntity
import io.github.shiaho777.logsleuth.app.data.db.FilterDao
import io.github.shiaho777.logsleuth.app.data.db.SessionDao
import io.github.shiaho777.logsleuth.app.data.db.SessionEntity
import io.github.shiaho777.logsleuth.app.ui.stream.UiLogEntry
import java.io.File
import javax.inject.Inject
import io.github.shiaho777.logsleuth.app.core.logcat.LogcatEntry
import io.github.shiaho777.logsleuth.app.core.logcat.SessionLogMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SessionDetailUiState(
    val session: SessionEntity? = null,
    val entries: List<UiLogEntry> = emptyList(),
    val crashes: List<CrashEventEntity> = emptyList(),
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val filter: LogFilter = LogFilter.DEFAULT,
    val regexInvalid: Boolean = false,
    val apps: List<AppChoice> = emptyList(),
    val presets: List<LogFilter> = emptyList(),
    val loading: Boolean = true,
    val missingFile: Boolean = false,
    val remoteEndpoint: String? = null,
    val missedEntries: Long = 0L,
    val sourceChanged: Boolean = false,
    val searching: Boolean = false,
    val searchQuery: String = "",
    val searchHits: List<Int> = emptyList(),
    val searchHitIndex: Int = -1,
    val searchCaseSensitive: Boolean = false,
    val filteredOut: Boolean = false,
)

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionDao: SessionDao,
    private val crashEventDao: CrashEventDao,
    private val bookmarkDao: BookmarkDao,
    private val exporter: SessionExporter,
    private val contextStore: CrashContextStore,
    private val installedApps: InstalledApps,
    private val filterDao: FilterDao,
    private val settingsRepository: io.github.shiaho777.logsleuth.app.data.prefs.SettingsRepository,
) : ViewModel() {

    private val sessionId: Long = checkNotNull(savedStateHandle["sessionId"])

    private val _ui = MutableStateFlow(SessionDetailUiState())
    val ui: StateFlow<SessionDetailUiState> = _ui.asStateFlow()

    private var all = emptyList<UiLogEntry>()

    /** Set once the session file is parsed; until then there is nothing to filter. */
    private var parsed = false
    private var filterJob: Job? = null
    private var searchJob: Job? = null

    /** A reset requested by a search job that a newer request cancelled. */
    private var resetHitIndex = false

    @Volatile
    private var enabledStack = FilterStack.PASS

    init {
        viewModelScope.launch {
            val session = sessionDao.getById(sessionId)
            _ui.update { it.copy(session = session) }
            if (session == null) {
                _ui.update { it.copy(loading = false, missingFile = true) }
                return@launch
            }
            val (entries, metadata, fileMissing) = parseFile(session)
            all = entries
            val apps = installedApps.loadForCapture(metadata.remotePackages.takeIf { metadata.remoteEndpoint != null })
            _ui.update {
                it.copy(
                    missingFile = fileMissing,
                    apps = apps,
                    remoteEndpoint = metadata.remoteEndpoint,
                    missedEntries = metadata.missedEntries,
                    sourceChanged = metadata.sourceChanged,
                )
            }
            parsed = true
            requestFilter()
        }
        viewModelScope.launch {
            crashEventDao.observeForSession(sessionId).collect { c -> _ui.update { it.copy(crashes = c) } }
        }
        viewModelScope.launch {
            bookmarkDao.observeForSession(sessionId).collect { b -> _ui.update { it.copy(bookmarks = b) } }
        }
        viewModelScope.launch {
            settingsRepository.settings.collect { s ->
                val changed = s.searchCaseSensitive != _ui.value.searchCaseSensitive
                _ui.update { it.copy(searchCaseSensitive = s.searchCaseSensitive) }
                if (changed && _ui.value.searchQuery.isNotBlank()) requestHits(resetIndex = true)
            }
        }
        viewModelScope.launch {
            filterDao.observeAll().collect { list ->
                val models = list.map { it.toLogFilter() }
                enabledStack = FilterStack.compile(models, ::resolveUid)
                _ui.update { it.copy(presets = models) }
                requestFilter()
            }
        }
    }

    fun rename(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            sessionDao.rename(sessionId, trimmed)
            _ui.update { it.copy(session = it.session?.copy(name = trimmed)) }
        }
    }

    suspend fun readContext(crash: CrashEventEntity): String? =
        withContext(Dispatchers.IO) { contextStore.read(crash.contextPath) }

    fun setFilter(filter: LogFilter) {
        _ui.update { it.copy(filter = filter) }
        requestFilter()
    }

    fun applyPreset(preset: LogFilter) = setFilter(preset)

    fun setRuleEnabled(filter: LogFilter, enabled: Boolean) {
        viewModelScope.launch { filterDao.update(filter.copy(enabled = enabled).toEntity()) }
    }

    fun disableEnabledRules() {
        viewModelScope.launch {
            _ui.value.presets.filter { it.enabled }.forEach { rule ->
                filterDao.update(rule.copy(enabled = false).toEntity())
            }
        }
    }

    fun savePreset(name: String) {
        viewModelScope.launch {
            filterDao.insert(_ui.value.filter.copy(name = name, id = 0).toEntity())
        }
    }

    /**
     * Re-filters [all] against the filter and rules in effect now. Each request
     * cancels the one before it, and both start and publish on the main thread,
     * so a slow scan for an older filter can never land after a newer one.
     */
    private fun requestFilter() {
        if (!parsed) return
        filterJob?.cancel()
        filterJob = viewModelScope.launch {
            val filter = _ui.value.filter
            val stack = enabledStack
            val source = all
            val (compiled, filtered) = withContext(Dispatchers.Default) {
                val compiled = CompiledFilter(filter, resolveUid(filter.packageName))
                compiled to source.filter {
                    ensureActive()
                    compiled.matches(it.entry) && stack.matches(it.entry)
                }
            }
            _ui.update {
                it.copy(
                    entries = filtered,
                    regexInvalid = compiled.regexInvalid || stack.regexInvalid,
                    filteredOut = source.isNotEmpty() && filtered.isEmpty(),
                    loading = false,
                )
            }
            requestHits()
        }
    }

    fun setSearching(searching: Boolean) {
        searchJob?.cancel()
        resetHitIndex = false
        _ui.update {
            it.copy(searching = searching, searchQuery = "", searchHits = emptyList(), searchHitIndex = -1)
        }
    }

    fun setSearchQuery(query: String) {
        _ui.update { it.copy(searchQuery = query) }
        requestHits(resetIndex = true, debounce = true)
    }

    fun setSearchCaseSensitive(value: Boolean) {
        viewModelScope.launch { settingsRepository.setSearchCaseSensitive(value) }
    }

    fun nextSearchHit(direction: Int) {
        _ui.update { state ->
            if (state.searchHits.isEmpty()) state
            else state.copy(
                searchHitIndex = (state.searchHitIndex + direction).mod(state.searchHits.size),
            )
        }
    }

    /**
     * Recomputes hits over the published entries off the main thread. Like
     * [requestFilter], a newer request cancels the older one; a reset the
     * cancelled one asked for carries over. Typing is debounced so a burst of
     * keys costs one scan.
     */
    private fun requestHits(resetIndex: Boolean = false, debounce: Boolean = false) {
        searchJob?.cancel()
        resetHitIndex = resetHitIndex || resetIndex
        searchJob = viewModelScope.launch {
            if (debounce) delay(SEARCH_DEBOUNCE_MS)
            val state = _ui.value
            val query = state.searchQuery
            val ignore = !state.searchCaseSensitive
            val entries = state.entries
            val hits = if (query.isBlank()) {
                emptyList()
            } else {
                withContext(Dispatchers.Default) {
                    entries.mapIndexedNotNull { index, uiEntry ->
                        ensureActive()
                        val e = uiEntry.entry
                        if (e.tag.contains(query, ignore) || e.message.contains(query, ignore)) index else null
                    }
                }
            }
            val reset = resetHitIndex
            resetHitIndex = false
            _ui.update {
                it.copy(
                    searchHits = hits,
                    searchHitIndex = when {
                        hits.isEmpty() -> -1
                        reset -> 0
                        else -> it.searchHitIndex.coerceIn(0, hits.lastIndex)
                    },
                )
            }
        }
    }

    private data class ParsedSession(
        val entries: List<UiLogEntry>,
        val metadata: SessionLogMetadata,
        val missing: Boolean = false,
    )

    private suspend fun parseFile(session: SessionEntity): ParsedSession =
        withContext(Dispatchers.IO) {
            val file = File(session.filePath)
            val endpoint = session.remoteEndpoint ?: "".takeIf { session.accessKind == "REMOTE_ADB" }
            val metadata = SessionLogMetadata(endpoint)
            if (!file.isFile) return@withContext ParsedSession(emptyList(), metadata, missing = true)
            val assembler = EntryAssembler()
            val out = ArrayList<UiLogEntry>(session.lineCount.coerceIn(16, 200_000).toInt())
            var seq = 0L
            fun add(entry: LogcatEntry) {
                val sourced = if (metadata.remoteEndpoint == null) entry else entry.copy(
                    remoteEndpoint = metadata.remoteEndpoint,
                    remotePackage = entry.uid?.let(metadata.remotePackages::get),
                )
                out.add(UiLogEntry(seq++, sourced))
            }
            file.bufferedReader().useLines { lines ->
                for (line in lines) {
                    ensureActive()
                    metadata.consume(line)
                    if (line.startsWith("#") || line.startsWith("=====")) continue
                    assembler.onLine(line).forEach(::add)
                }
            }
            assembler.flush()?.let(::add)
            ParsedSession(out, metadata)
        }

    private fun resolveUid(packageName: String?): Int? = installedApps.resolveUid(packageName)

    fun share(format: SessionExporter.Format, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            onResult(exporter.exportAndShare(sessionId, format))
        }
    }

    fun saveToUri(format: SessionExporter.Format, uri: android.net.Uri, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            onResult(exporter.exportToUri(sessionId, format, uri))
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
    }
}
