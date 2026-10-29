package io.github.shiaho777.logsleuth.app.core.crashes

import io.github.shiaho777.logsleuth.app.core.apps.InstalledApps
import io.github.shiaho777.logsleuth.app.core.detect.CrashSignal
import io.github.shiaho777.logsleuth.app.core.logcat.LogcatEntry
import io.github.shiaho777.logsleuth.app.data.db.CrashEventDao
import io.github.shiaho777.logsleuth.app.data.db.CrashEventEntity
import io.github.shiaho777.logsleuth.app.service.NotificationHelper
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Turns a [CrashSignal] into a stored event, a surrounding-log file and/or a
 * notification. Split out of `LogcatEngine` so the engine stays about owning
 * the logcat process, and so this policy is testable without a stream.
 *
 * Saving and notifying are independent: a crash can be announced without a
 * row, and a muted app still gets its record.
 */
@Singleton
class CrashRecorder @Inject constructor(
    private val crashEventDao: CrashEventDao,
    private val installedApps: InstalledApps,
    private val contextStore: CrashContextStore,
    private val notificationHelper: NotificationHelper,
) {
    /** What the user asked us to keep and ping about. Updated from settings. */
    @Volatile
    var capture: CrashCapture = CrashCapture()

    /** One notification channel per crash type and package. */
    @Volatile
    var separateChannels: Boolean = true

    /** Packages the user asked us to stop recording crashes for. */
    @Volatile
    var blacklist: Set<String> = emptySet()

    /** Serializes dedupe → insert → notify so two lines cannot double-insert. */
    private val writeMutex = Mutex()

    /** Notify-only crashes have no Room row to dedupe against. */
    private val recentNotifyKeys = ArrayDeque<String>()
    private val recentNotifySet = HashSet<String>()

    /** True when this crash's app was explicitly muted by the user. */
    fun ignores(packageName: String?): Boolean =
        packageName != null && packageName in blacklist

    /**
     * Handles one signal. [snapshot] is only called when the surrounding lines
     * are actually needed — a 20k-entry copy per muted crash would be waste.
     */
    suspend fun record(
        signal: CrashSignal,
        sessionId: Long?,
        snapshot: suspend () -> List<LogcatEntry>,
    ) {
        val settings = capture
        val store = CrashPolicy.stores(signal.type, settings)
        val notify = CrashPolicy.notifies(signal.type, settings)
        if (!store && !notify) return

        val key = "${signal.timeMillis}|${signal.pid}|${signal.type.name}|${signal.firstLine}"
        writeMutex.withLock {
            if (store) {
                // logcat replays its buffers on every start, so the same fatal
                // line can arrive twice. The row is the identity for that.
                if (crashEventDao.findExisting(
                        time = signal.timeMillis,
                        pid = signal.pid,
                        type = signal.type.name,
                        firstLine = signal.firstLine,
                    ) != null
                ) {
                    return@withLock
                }
                val identity = signal.packageName?.let { installedApps.identity(it) }
                val eventId = crashEventDao.insert(
                    CrashEventEntity(
                        sessionId = sessionId,
                        time = signal.timeMillis,
                        pid = signal.pid,
                        packageName = signal.packageName,
                        type = signal.type.name,
                        firstLine = signal.firstLine,
                        snippet = signal.snippet,
                        appName = identity?.label,
                        versionName = identity?.versionName,
                        versionCode = identity?.versionCode,
                    ),
                )
                val text = CrashContext.format(
                    CrashContext.lines(snapshot(), signal.timeMillis),
                )
                val path = withContext(Dispatchers.IO) {
                    runCatching { contextStore.write(eventId, text) }.getOrNull()
                }
                if (path != null) crashEventDao.setContextPath(eventId, path)
                if (notify) {
                    rememberNotified(key)
                    notificationHelper.notifyCrash(
                        signal,
                        eventId,
                        identity?.label,
                        separateChannels,
                    )
                }
            } else if (notify && !rememberNotified(key)) {
                val label = signal.packageName?.let { installedApps.identity(it)?.label }
                notificationHelper.notifyCrash(signal, 0L, label, separateChannels)
            }
        }
    }

    /** True when [key] was already announced. Records it otherwise. */
    private fun rememberNotified(key: String): Boolean {
        if (!recentNotifySet.add(key)) return true
        recentNotifyKeys.addLast(key)
        while (recentNotifyKeys.size > NOTIFY_MEMORY) {
            recentNotifySet.remove(recentNotifyKeys.removeFirst())
        }
        return false
    }

    private companion object {
        /** How many announce-only crashes are remembered for dedupe. */
        const val NOTIFY_MEMORY = 256
    }
}
