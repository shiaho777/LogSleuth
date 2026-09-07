package io.github.logsleuth.app.ui.filters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.logsleuth.app.core.filter.LogFilter
import io.github.logsleuth.app.core.logcat.LogLevel
import io.github.logsleuth.app.data.db.FilterDao
import io.github.logsleuth.app.data.db.FilterEntity
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class FiltersViewModel @Inject constructor(
    private val filterDao: FilterDao,
) : ViewModel() {

    val presets: StateFlow<List<LogFilter>> = filterDao.observeAll()
        .map { list -> list.map(::toModel) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(filter: LogFilter) {
        viewModelScope.launch { filterDao.insert(toEntity(filter.copy(id = 0))) }
    }

    fun delete(filter: LogFilter) {
        viewModelScope.launch { filterDao.delete(toEntity(filter)) }
    }

    private fun toEntity(f: LogFilter) = FilterEntity(
        id = f.id,
        name = f.name,
        minLevel = f.minLevel.name,
        query = f.query,
        excludeQuery = f.excludeQuery,
        tagQuery = f.tagQuery,
        useRegex = f.useRegex,
        packageName = f.packageName,
    )

    private fun toModel(e: FilterEntity) = LogFilter(
        id = e.id,
        name = e.name,
        minLevel = LogLevel.valueOf(e.minLevel),
        query = e.query,
        excludeQuery = e.excludeQuery,
        tagQuery = e.tagQuery,
        useRegex = e.useRegex,
        packageName = e.packageName,
    )
}
