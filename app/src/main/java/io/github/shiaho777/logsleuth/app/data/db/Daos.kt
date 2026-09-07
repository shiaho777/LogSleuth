package io.github.shiaho777.logsleuth.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getById(id: Long): SessionEntity?

    @Insert
    suspend fun insert(entity: SessionEntity): Long

    @Update
    suspend fun update(entity: SessionEntity)

    @Query("UPDATE sessions SET endedAt = :endedAt, lineCount = :lineCount WHERE id = :id")
    suspend fun finish(id: Long, endedAt: Long, lineCount: Long)

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
    @Query("SELECT * FROM crash_events ORDER BY time DESC")
    fun observeAll(): Flow<List<CrashEventEntity>>

    @Query("SELECT * FROM crash_events WHERE sessionId = :sessionId ORDER BY time ASC")
    fun observeForSession(sessionId: Long): Flow<List<CrashEventEntity>>

    @Insert
    suspend fun insert(entity: CrashEventEntity): Long

    @Query("DELETE FROM crash_events WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM crash_events WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: Long)
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
