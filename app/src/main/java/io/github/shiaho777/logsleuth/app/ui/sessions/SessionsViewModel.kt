package io.github.shiaho777.logsleuth.app.ui.sessions

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.shiaho777.logsleuth.app.core.export.SessionExporter
import io.github.shiaho777.logsleuth.app.core.importer.LogImporter
import io.github.shiaho777.logsleuth.app.data.db.BookmarkDao
import io.github.shiaho777.logsleuth.app.data.db.CrashEventDao
import io.github.shiaho777.logsleuth.app.data.db.SessionDao
import io.github.shiaho777.logsleuth.app.data.db.SessionEntity
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class SessionsViewModel @Inject constructor(
    private val sessionDao: SessionDao,
    private val crashEventDao: CrashEventDao,
    private val bookmarkDao: BookmarkDao,
    private val exporter: SessionExporter,
    private val importer: LogImporter,
) : ViewModel() {

    val sessions: StateFlow<List<SessionEntity>> = sessionDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Session hidden pending the undo window; deleted for real on expiry. */
    private val _pendingDelete = MutableStateFlow<SessionEntity?>(null)
    val pendingDelete: StateFlow<SessionEntity?> = _pendingDelete.asStateFlow()

    private var deleteJob: Job? = null

    fun requestDelete(session: SessionEntity) {
        // A new request commits the previous pending delete — otherwise the
        // first item would silently reappear when the pending slot is replaced.
        _pendingDelete.value?.let { delete(it) }
        _pendingDelete.value = session
        deleteJob?.cancel()
        deleteJob = viewModelScope.launch {
            delay(4_000)
            _pendingDelete.value = null
            delete(session)
        }
    }

    fun undoDelete() {
        deleteJob?.cancel()
        _pendingDelete.value = null
    }

    private fun delete(session: SessionEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { File(session.filePath).delete() }
            crashEventDao.deleteForSession(session.id)
            bookmarkDao.deleteForSession(session.id)
            sessionDao.delete(session)
        }
    }

    fun share(session: SessionEntity, format: SessionExporter.Format) {
        viewModelScope.launch {
            exporter.export(session.id, format).onSuccess { file ->
                exporter.share(
                    file,
                    if (format == SessionExporter.Format.ZIP) "application/zip" else "text/plain",
                )
            }
        }
    }

    /** Imports an externally shared log file or SDK export zip. */
    fun import(uri: Uri, onResult: (Result<Long>) -> Unit) {
        viewModelScope.launch {
            onResult(importer.import(uri))
        }
    }
}
