package io.github.shiaho777.logsleuth.app.ui.filters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.shiaho777.logsleuth.app.core.apps.AppChoice
import io.github.shiaho777.logsleuth.app.core.apps.InstalledApps
import io.github.shiaho777.logsleuth.app.core.filter.FilterExchange
import io.github.shiaho777.logsleuth.app.core.filter.LogFilter
import io.github.shiaho777.logsleuth.app.data.db.FilterDao
import io.github.shiaho777.logsleuth.app.data.db.toEntity
import io.github.shiaho777.logsleuth.app.data.db.toLogFilter
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class FiltersViewModel @Inject constructor(
    private val filterDao: FilterDao,
    private val installedApps: InstalledApps,
) : ViewModel() {

    private val _apps = MutableStateFlow<List<AppChoice>>(emptyList())
    val apps: StateFlow<List<AppChoice>> = _apps.asStateFlow()

    val presets: StateFlow<List<LogFilter>> = filterDao.observeAll()
        .map { list -> list.map { it.toLogFilter() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { _apps.value = installedApps.load() }
    }

    fun save(filter: LogFilter) {
        viewModelScope.launch {
            val entity = filter.toEntity()
            if (filter.id == 0L) filterDao.insert(entity) else filterDao.update(entity)
        }
    }

    fun delete(filter: LogFilter) {
        viewModelScope.launch { filterDao.delete(filter.toEntity()) }
    }

    fun setEnabled(filter: LogFilter, enabled: Boolean) {
        viewModelScope.launch { filterDao.update(filter.copy(enabled = enabled).toEntity()) }
    }

    fun setIncluding(filter: LogFilter, including: Boolean) {
        viewModelScope.launch { filterDao.update(filter.copy(including = including).toEntity()) }
    }

    fun exportText(): String = FilterExchange.encode(presets.value)

    /** Inserts rules from an export file. Returns how many were added, or null if the file is not ours. */
    fun importText(text: String): Int? {
        val parsed = FilterExchange.decode(text) ?: return null
        viewModelScope.launch {
            parsed.forEach { filterDao.insert(it.copy(id = 0).toEntity()) }
        }
        return parsed.size
    }
}
