package io.github.logsleuth.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val filePath: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val lineCount: Long = 0,
    /** Whether the log file was captured with the uid column (per-app filter
     * available on replay) and which access path produced it. */
    val accessKind: String = "",
    val filterPackage: String? = null,
    val imported: Boolean = false,
)

@Entity(tableName = "filters")
data class FilterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val minLevel: String = "V",
    val query: String = "",
    val excludeQuery: String = "",
    val tagQuery: String = "",
    val useRegex: Boolean = false,
    val packageName: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "crash_events")
data class CrashEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long? = null,
    val time: Long,
    val pid: Int,
    val packageName: String? = null,
    val type: String, // CRASH | ANR
    val firstLine: String,
    val snippet: String,
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long? = null,
    val time: Long,
    val note: String = "",
)
