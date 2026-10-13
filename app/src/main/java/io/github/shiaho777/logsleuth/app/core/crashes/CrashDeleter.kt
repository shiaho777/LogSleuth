package io.github.shiaho777.logsleuth.app.core.crashes

import io.github.shiaho777.logsleuth.app.data.db.CrashEventDao
import javax.inject.Inject
import javax.inject.Singleton

/** Removes crash rows and the surrounding-log files that belong to them. */
@Singleton
class CrashDeleter @Inject constructor(
    private val crashEventDao: CrashEventDao,
    private val contextStore: CrashContextStore,
) {
    suspend fun deleteIds(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        val list = ids.toList()
        crashEventDao.contextPathsForIds(list).forEach { contextStore.delete(it) }
        crashEventDao.deleteByIds(list)
    }

    suspend fun deleteSession(sessionId: Long) {
        crashEventDao.contextPathsForSession(sessionId).forEach { contextStore.delete(it) }
        crashEventDao.deleteForSession(sessionId)
    }
}
