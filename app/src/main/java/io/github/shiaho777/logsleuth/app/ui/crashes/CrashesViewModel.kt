package io.github.shiaho777.logsleuth.app.ui.crashes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.shiaho777.logsleuth.app.core.crashes.CrashGroup
import io.github.shiaho777.logsleuth.app.core.crashes.CrashSort
import io.github.shiaho777.logsleuth.app.core.crashes.groupCrashes
import io.github.shiaho777.logsleuth.app.core.crashes.CrashDeleter
import io.github.shiaho777.logsleuth.app.data.db.CrashEventDao
import io.github.shiaho777.logsleuth.app.data.prefs.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CrashBrowserState(
    val query: String = "",
    val sort: CrashSort = CrashSort.NEWEST,
    val reversed: Boolean = false,
)

@HiltViewModel
class CrashesViewModel @Inject constructor(
    private val crashEventDao: CrashEventDao,
    private val deleter: CrashDeleter,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val crashes = crashEventDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val browser = MutableStateFlow(CrashBrowserState())

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { s ->
                val sort = runCatching { CrashSort.valueOf(s.crashSort) }.getOrDefault(CrashSort.NEWEST)
                val current = browser.value
                if (current.sort != sort || current.reversed != s.crashSortReversed) {
                    browser.value = current.copy(sort = sort, reversed = s.crashSortReversed)
                }
            }
        }
    }

    /** Ids hidden during the undo window. Committed when it expires. */
    private val _pendingIds = MutableStateFlow<Set<Long>>(emptySet())
    val pendingDelete: StateFlow<Boolean> = _pendingIds
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val groups: StateFlow<List<CrashGroup>> = combine(crashes, browser, _pendingIds) { rows, state, hidden ->
        groupCrashes(
            rows.filter { it.id !in hidden },
            query = state.query,
            sort = state.sort,
            reversed = state.reversed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val browserState: StateFlow<CrashBrowserState> = browser

    private var deleteJob: Job? = null

    fun setQuery(query: String) {
        browser.value = browser.value.copy(query = query)
    }

    fun setSort(sort: CrashSort) {
        browser.value = browser.value.copy(sort = sort)
        viewModelScope.launch { settingsRepository.setCrashSort(sort.name) }
    }

    fun toggleReversed() {
        val next = !browser.value.reversed
        browser.value = browser.value.copy(reversed = next)
        viewModelScope.launch { settingsRepository.setCrashSortReversed(next) }
    }

    fun requestDeletePackage(packageName: String?) {
        commitPending()
        val ids = crashes.value
            .filter { it.packageName == packageName }
            .map { it.id }
            .toSet()
        if (ids.isEmpty()) return
        _pendingIds.value = ids
        deleteJob = viewModelScope.launch {
            delay(4_000)
            val pending = _pendingIds.value
            _pendingIds.value = emptySet()
            deleter.deleteIds(pending)
        }
    }

    fun undoDelete() {
        deleteJob?.cancel()
        _pendingIds.value = emptySet()
    }

    private fun commitPending() {
        val pending = _pendingIds.value
        deleteJob?.cancel()
        _pendingIds.value = emptySet()
        if (pending.isNotEmpty()) {
            viewModelScope.launch { deleter.deleteIds(pending) }
        }
    }
}
