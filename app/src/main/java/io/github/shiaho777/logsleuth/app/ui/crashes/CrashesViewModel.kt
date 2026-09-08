package io.github.shiaho777.logsleuth.app.ui.crashes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.shiaho777.logsleuth.app.core.export.SessionExporter
import io.github.shiaho777.logsleuth.app.data.db.CrashEventDao
import io.github.shiaho777.logsleuth.app.data.db.CrashEventEntity
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class CrashesViewModel @Inject constructor(
    private val crashEventDao: CrashEventDao,
    private val exporter: SessionExporter,
) : ViewModel() {

    val crashes: StateFlow<List<CrashEventEntity>> = crashEventDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(id: Long) {
        viewModelScope.launch { crashEventDao.deleteById(id) }
    }

    fun share(crash: CrashEventEntity) {
        viewModelScope.launch {
            val name = "crash_${crash.packageName ?: "unknown"}_${crash.id}.txt"
            exporter.shareSnippet(
                name,
                "${crash.type} in ${crash.packageName ?: "unknown app"}\n\n${crash.snippet}",
            )
        }
    }
}
