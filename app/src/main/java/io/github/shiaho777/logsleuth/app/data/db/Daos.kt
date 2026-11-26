package io.github.shiaho777.logsleuth.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

private const val REAL_CRASHES =
    "FROM crash_events LEFT JOIN sessions ON sessions.id = crash_events.sessionId " +
        "WHERE COALESCE(sessions.accessKind, '') != '$EXAMPLE_SESSION_KIND'"

@Dao
interface SessionDao {
    // id DESC = creation order. Snapshots save a buffer whose startedAt
    // can be far in the past — ordering by it sinks a just-saved row.
    @Query("SELECT * FROM sessions ORDER BY id DESC")
    fun observeAll(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getById(id: Long): SessionEntity?

    @Query("SELECT * FROM sessions WHERE accessKind = '$EXAMPLE_SESSION_KIND' LIMIT 1")
    suspend fun findExample(): SessionEntity?

    @Insert
    suspend fun insert(entity: SessionEntity): Long

    @Update
    suspend fun update(entity: SessionEntity)

    @Query("UPDATE sessions SET endedAt = :endedAt, lineCount = :lineCount WHERE id = :id")
    suspend fun finish(id: Long, endedAt: Long, lineCount: Long)

    /** Sessions left open by a process death (endedAt never written). */
    @Query("SELECT * FROM sessions WHERE endedAt IS NULL")
    suspend fun unfinished(): List<SessionEntity>

    @Query("UPDATE sessions SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Delete
    suspend fun delete(entity: SessionEntity)
}

@Dao
interface FilterDao {
    @Query("SELECT * FROM filters ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<FilterEntity>>

    @Insert
    suspend fun insert(entity: FilterEntity): Long

    @Update
    suspend fun update(entity: FilterEntity)

    @Delete
    suspend fun delete(entity: FilterEntity)
}

@Dao
interface CrashEventDao {
    @Query("SELECT crash_events.* $REAL_CRASHES ORDER BY crash_events.time DESC")
    fun observeAll(): Flow<List<CrashEventEntity>>

    @Query("SELECT * FROM crash_events WHERE sessionId = :sessionId ORDER BY time ASC")
    fun observeForSession(sessionId: Long): Flow<List<CrashEventEntity>>

    @Query("SELECT * FROM crash_events WHERE id = :id")
    fun observeById(id: Long): Flow<CrashEventEntity?>

    @Query(
        "SELECT EXISTS(SELECT 1 FROM crash_events JOIN sessions ON sessions.id = crash_events.sessionId " +
            "WHERE crash_events.id = :id AND sessions.accessKind = '$EXAMPLE_SESSION_KIND')",
    )
    fun observeIsExample(id: Long): Flow<Boolean>

    @Insert
    suspend fun insert(entity: CrashEventEntity): Long

    /** Same log line seen again (logcat replays its buffers on every start). */
    @Query(
        "SELECT crash_events.id $REAL_CRASHES AND crash_events.time = :time AND pid = :pid AND type = :type " +
            "AND firstLine = :firstLine AND crash_events.remoteEndpoint IS :remoteEndpoint LIMIT 1",
    )
    suspend fun findExisting(
        time: Long,
        pid: Int,
        type: String,
        firstLine: String,
        remoteEndpoint: String?,
    ): Long?

    @Query("DELETE FROM crash_events WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM crash_events WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: Long)

    @Query("UPDATE crash_events SET contextPath = :path WHERE id = :id")
    suspend fun setContextPath(id: Long, path: String)

    @Query(
        "SELECT contextPath FROM crash_events WHERE sessionId = :sessionId " +
            "AND contextPath IS NOT NULL",
    )
    suspend fun contextPathsForSession(sessionId: Long): List<String>

    @Query("SELECT contextPath FROM crash_events WHERE id = :id")
    suspend fun contextPath(id: Long): String?

    @Query(
        "SELECT contextPath FROM crash_events WHERE id IN (:ids) " +
            "AND contextPath IS NOT NULL",
    )
    suspend fun contextPathsForIds(ids: List<Long>): List<String>

    @Query("DELETE FROM crash_events WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /** Retention pass over every stored event (bounded by the retention caps). */
    @Query("SELECT crash_events.* $REAL_CRASHES")
    suspend fun all(): List<CrashEventEntity>
}

@Dao
interface AutomationRuleDao {
    @Query("SELECT * FROM automation_rules ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<AutomationRuleEntity>>

    @Insert
    suspend fun insert(entity: AutomationRuleEntity): Long

    @Update
    suspend fun update(entity: AutomationRuleEntity)

    @Delete
    suspend fun delete(entity: AutomationRuleEntity)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY time DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE sessionId = :sessionId ORDER BY time ASC")
    fun observeForSession(sessionId: Long): Flow<List<BookmarkEntity>>

    @Insert
    suspend fun insert(entity: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: Long)
}
