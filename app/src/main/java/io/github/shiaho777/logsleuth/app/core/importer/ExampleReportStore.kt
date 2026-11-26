package io.github.shiaho777.logsleuth.app.core.importer

import android.content.Context
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.shiaho777.logsleuth.app.R
import io.github.shiaho777.logsleuth.app.core.detect.CrashDetector
import io.github.shiaho777.logsleuth.app.core.logcat.EntryAssembler
import io.github.shiaho777.logsleuth.app.data.db.AppDatabase
import io.github.shiaho777.logsleuth.app.data.db.BookmarkEntity
import io.github.shiaho777.logsleuth.app.data.db.CrashEventEntity
import io.github.shiaho777.logsleuth.app.data.db.EXAMPLE_SESSION_KIND
import io.github.shiaho777.logsleuth.app.data.db.SessionEntity
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class ExampleReportStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
) {
    private val mutex = Mutex()

    suspend fun open(): Long = withContext(Dispatchers.IO) {
        mutex.withLock {
            val sessions = database.sessionDao()
            val existing = sessions.findExample()
            if (existing != null && File(existing.filePath).isFile) return@withLock existing.id

            val startedAt = System.currentTimeMillis() - 8_000L
            val lines = exampleReportLines(startedAt)
            val assembler = EntryAssembler()
            val entries = lines.flatMap(assembler::onLine) + listOfNotNull(assembler.flush())
            val detector = CrashDetector()
            val crash = (entries.mapNotNull(detector::onEntry) + detector.drain()).single()
            val directory = File(context.filesDir, "imports").apply { mkdirs() }
            val file = File.createTempFile("example_", ".log", directory)
            try {
                file.writeText(lines.joinToString("\n", postfix = "\n"))
                withContext(NonCancellable) {
                    database.withTransaction {
                        if (existing != null) {
                            database.crashEventDao().deleteForSession(existing.id)
                            database.bookmarkDao().deleteForSession(existing.id)
                            sessions.delete(existing)
                        }
                        val id = sessions.insert(
                            SessionEntity(
                                name = context.getString(R.string.example_report_name),
                                filePath = file.absolutePath,
                                startedAt = startedAt,
                                endedAt = startedAt + 6_000L,
                                lineCount = entries.size.toLong(),
                                accessKind = EXAMPLE_SESSION_KIND,
                            ),
                        )
                        database.crashEventDao().insert(
                            CrashEventEntity(
                                sessionId = id,
                                time = crash.timeMillis,
                                pid = crash.pid,
                                packageName = crash.packageName,
                                type = crash.type.name,
                                firstLine = crash.firstLine,
                                snippet = crash.snippet,
                            ),
                        )
                        database.bookmarkDao().insert(
                            BookmarkEntity(
                                sessionId = id,
                                time = startedAt + 3_000L,
                                note = context.getString(R.string.example_report_bookmark),
                            ),
                        )
                        id
                    }
                }
            } catch (failure: Exception) {
                file.delete()
                throw failure
            }
        }
    }
}

internal fun exampleReportLines(start: Long): List<String> {
    val formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS", Locale.ROOT).withZone(ZoneId.systemDefault())
    fun line(offset: Long, level: Char, tag: String, message: String): String =
        "${formatter.format(Instant.ofEpochMilli(start + offset))}  4242  4242 $level $tag: $message"
    return listOf(
        line(0, 'I', "Checkout", "CheckoutActivity opened"),
        line(850, 'D', "Cart", "Loaded two items from local cache"),
        line(1_800, 'I', "Network", "GET /checkout/quote"),
        line(3_000, 'I', "Checkout", "User tapped Pay"),
        line(4_100, 'W', "Network", "Quote response was missing total"),
        line(4_150, 'E', "AndroidRuntime", "FATAL EXCEPTION: main"),
        line(4_150, 'E', "AndroidRuntime", "Process: com.example.checkout, PID: 4242"),
        line(4_150, 'E', "AndroidRuntime", "java.lang.IllegalStateException: Checkout total is missing"),
        line(4_150, 'E', "AndroidRuntime", "\tat com.example.checkout.CheckoutActivity.confirm(CheckoutActivity.kt:47)"),
        line(4_150, 'E', "AndroidRuntime", "\tat com.example.checkout.CheckoutActivity.onPayClicked(CheckoutActivity.kt:32)"),
        line(4_500, 'W', "ActivityTaskManager", "Force finishing activity com.example.checkout/.CheckoutActivity"),
        line(6_000, 'I', "ActivityTaskManager", "Returned to the launcher"),
    )
}
