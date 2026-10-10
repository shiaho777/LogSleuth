package io.github.shiaho777.logsleuth.app.ui.sessiondetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.shiaho777.logsleuth.app.core.apps.AppChoice
import io.github.shiaho777.logsleuth.app.core.apps.InstalledApps
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
import kotlinx.coroutines.Dispatchers
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
)

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionDao: SessionDao,
    private val crashEventDao: CrashEventDao,
    private val bookmarkDao: BookmarkDao,
    private val exporter: SessionExporter,
    private val installedApps: InstalledApps,
    private val filterDao: FilterDao,
) : ViewModel() {

    private val sessionId: Long = checkNotNull(savedStateHandle["sessionId"])

    private val _ui = MutableStateFlow(SessionDetailUiState())
    val ui: StateFlow<SessionDetailUiState> = _ui.asStateFlow()

    private var all = emptyList<UiLogEntry>()

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
            all = parseFile(session)
            val fileMissing = withContext(Dispatchers.IO) { !File(session.filePath).exists() }
            applyFilter(_ui.value.filter)
            _ui.update { it.copy(loading = false, missingFile = all.isEmpty() && fileMissing) }
        }
        viewModelScope.launch {
            crashEventDao.observeForSession(sessionId).collect { c -> _ui.update { it.copy(crashes = c) } }
        }
        viewModelScope.launch {
            bookmarkDao.observeForSession(sessionId).collect { b -> _ui.update { it.copy(bookmarks = b) } }
        }
        viewModelScope.launch { _ui.update { it.copy(apps = installedApps.load()) } }
        viewModelScope.launch {
            filterDao.observeAll().collect { list ->
                val models = list.map { it.toLogFilter() }
                enabledStack = FilterStack.compile(models, ::resolveUid)
                _ui.update { it.copy(presets = models) }
                if (all.isNotEmpty()) applyFilter(_ui.value.filter)
            }
        }
    }

    fun setFilter(filter: LogFilter) {
        _ui.update { it.copy(filter = filter) }
        viewModelScope.launch { applyFilter(filter) }
    }

    fun applyPreset(preset: LogFilter) = setFilter(preset)

    fun savePreset(name: String) {
        viewModelScope.launch {
            filterDao.insert(_ui.value.filter.copy(name = name, id = 0).toEntity())
        }
    }

    private suspend fun applyFilter(filter: LogFilter) {
        val compiled = CompiledFilter(filter, resolveUid(filter.packageName))
        val stack = enabledStack
        val filtered = withContext(Dispatchers.Default) {
            all.filter { compiled.matches(it.entry) && stack.matches(it.entry) }
        }
        _ui.update {
            it.copy(entries = filtered, regexInvalid = compiled.regexInvalid || stack.regexInvalid)
        }
    }

    private suspend fun parseFile(session: SessionEntity): List<UiLogEntry> =
        withContext(Dispatchers.IO) {
            val file = File(session.filePath)
            if (!file.exists()) return@withContext emptyList()
            val assembler = EntryAssembler()
            val out = ArrayList<UiLogEntry>(session.lineCount.toInt().coerceAtLeast(16))
            var seq = 0L
            file.bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (line.startsWith("#") || line.startsWith("=====")) continue
                    out += assembler.onLine(line).map { UiLogEntry(seq++, it) }
                }
            }
            assembler.flush()?.let { out += UiLogEntry(seq++, it) }
            out
        }

    // The recorded uid column is from the recording device — resolution is
    // only meaningful for sessions captured on this device (imported SDK
    // bundles carry no uid column and match nothing either way).
    private fun resolveUid(packageName: String?): Int? = installedApps.resolveUid(packageName)

    fun share(format: SessionExporter.Format) {
        viewModelScope.launch {
            exporter.export(sessionId, format).onSuccess { file ->
                exporter.share(file, if (format == SessionExporter.Format.ZIP) "application/zip" else "text/plain")
            }
        }
    }
}
