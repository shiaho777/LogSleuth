package io.github.shiaho777.logsleuth.app.core.sessions

import io.github.shiaho777.logsleuth.app.core.crashes.CrashDeleter
import io.github.shiaho777.logsleuth.app.data.db.BookmarkDao
import io.github.shiaho777.logsleuth.app.data.db.SessionDao
import io.github.shiaho777.logsleuth.app.service.RecordingManager
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class SessionDeleter @Inject constructor(
    private val sessions: SessionDao,
    private val bookmarks: BookmarkDao,
    private val crashes: CrashDeleter,
    private val recording: RecordingManager,
) {
    private val lock = Mutex()

    suspend fun delete(id: Long): Boolean = withContext(Dispatchers.IO) {
        lock.withLock {
            val session = sessions.getById(id) ?: return@withLock true
            if (recording.state.value.sessionId == id) return@withLock false
            val file = File(session.filePath)
            if (file.exists() && !file.delete()) return@withLock false
            crashes.deleteSession(id)
            bookmarks.deleteForSession(id)
            sessions.delete(session)
            true
        }
    }
}
