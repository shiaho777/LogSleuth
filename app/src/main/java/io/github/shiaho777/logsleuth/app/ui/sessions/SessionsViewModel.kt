package io.github.shiaho777.logsleuth.app.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.shiaho777.logsleuth.app.core.export.SessionExporter
import io.github.shiaho777.logsleuth.app.data.db.BookmarkDao
import io.github.shiaho777.logsleuth.app.data.db.CrashEventDao
import io.github.shiaho777.logsleuth.app.data.db.SessionDao
import io.github.shiaho777.logsleuth.app.data.db.SessionEntity
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class SessionsViewModel @Inject constructor(
    private val sessionDao: SessionDao,
    private val crashEventDao: CrashEventDao,
    private val bookmarkDao: BookmarkDao,
    private val exporter: SessionExporter,
) : ViewModel() {

    val sessions: StateFlow<List<SessionEntity>> = sessionDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(session: SessionEntity) {
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
                exporter.share(file, if (format == SessionExporter.Format.ZIP) "application/zip" else "text/plain")
            }
        }
    }
}
