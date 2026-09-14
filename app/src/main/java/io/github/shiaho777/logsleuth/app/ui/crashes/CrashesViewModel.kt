package io.github.shiaho777.logsleuth.app.ui.crashes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.shiaho777.logsleuth.app.core.export.SessionExporter
import io.github.shiaho777.logsleuth.app.data.db.CrashEventDao
import io.github.shiaho777.logsleuth.app.data.db.CrashEventEntity
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class CrashesViewModel @Inject constructor(
    private val crashEventDao: CrashEventDao,
    private val exporter: SessionExporter,
) : ViewModel() {

    val crashes: StateFlow<List<CrashEventEntity>> = crashEventDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Crash event hidden pending the undo window; deleted for real on expiry. */
    private val _pendingDelete = MutableStateFlow<CrashEventEntity?>(null)
    val pendingDelete: StateFlow<CrashEventEntity?> = _pendingDelete.asStateFlow()

    private var deleteJob: Job? = null

    fun requestDelete(crash: CrashEventEntity) {
        _pendingDelete.value?.let { delete(it.id) }
        _pendingDelete.value = crash
        deleteJob?.cancel()
        deleteJob = viewModelScope.launch {
            delay(4_000)
            _pendingDelete.value = null
            delete(crash.id)
        }
    }

    fun undoDelete() {
        deleteJob?.cancel()
        _pendingDelete.value = null
    }

    private fun delete(id: Long) {
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
