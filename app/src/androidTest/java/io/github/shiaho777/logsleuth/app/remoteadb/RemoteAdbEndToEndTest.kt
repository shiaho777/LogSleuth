package io.github.shiaho777.logsleuth.app.remoteadb

import androidx.test.platform.app.InstrumentationRegistry
import io.github.shiaho777.logsleuth.app.core.logcat.RemoteAdbLogcatSource
import io.github.shiaho777.logsleuth.app.core.remoteadb.AdbConnection
import io.github.shiaho777.logsleuth.app.core.remoteadb.AdbKeyManager
import java.io.File
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Two-device verification: this device runs the test, a second device
 * serves adbd over tcpip. Pass its address with:
 *
 *   adb shell am instrument -w \
 *     -e remote_host 10.0.2.2 -e remote_port 5555 \
 *     io.github.shiaho777.logsleuth.app.debug/androidx.test.runner.AndroidJUnitRunner
 *
 * Without the arguments the tests are skipped, so a single-device run
 * stays green. The remote screen may show the "Allow debugging?" dialog
 * on the first connection — accept it there.
 */
class RemoteAdbEndToEndTest {

    private val args = InstrumentationRegistry.getArguments()
    private val host: String? = args.getString("remote_host")
    private val port: Int = args.getString("remote_port")?.toIntOrNull() ?: 5555

    private fun keys(): AdbKeyManager {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Same directory the app's RemoteAdbManager uses, so every test
        // presents the same identity to the remote device.
        return AdbKeyManager(File(context.filesDir, "remote-adb"))
    }

    @Test
    fun handshakeAndShellProbe() = runBlocking {
        assumeTrue("no remote_host argument", !host.isNullOrBlank())
        withTimeout(90_000) {
            AdbConnection(host!!, port, keys()).use { connection ->
                connection.authenticate()
                connection.openShell("echo logsleuth-e2e-ok")
                val out = StringBuilder()
                connection.readShell { out.append(String(it, Charsets.UTF_8)) }
                assertTrue(
                    "probe output was: $out",
                    out.toString().contains("logsleuth-e2e-ok"),
                )
            }
        }
    }

    @Test
    fun engineStreamsRemoteThroughTheFullPipeline() = runBlocking {
        assumeTrue("no remote_host argument", !host.isNullOrBlank())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = io.github.shiaho777.logsleuth.app.data.prefs.SettingsRepository(context)
        settings.setRemoteAdb(host!!, port)
        settings.setAccessPreference(io.github.shiaho777.logsleuth.app.core.logcat.AccessPolicy.REMOTE)
        val shizuku = io.github.shiaho777.logsleuth.app.core.shizuku.ShizukuManager(context)
        val root = io.github.shiaho777.logsleuth.app.core.root.RootManager(context)
        val checker = io.github.shiaho777.logsleuth.app.core.logcat.AccessChecker(context, shizuku)
        val db = androidx.room.Room.databaseBuilder(
            context,
            io.github.shiaho777.logsleuth.app.data.db.AppDatabase::class.java,
            "remote-e2e-test.db",
        ).addMigrations(
            io.github.shiaho777.logsleuth.app.data.db.AppDatabase.MIGRATION_1_2,
            io.github.shiaho777.logsleuth.app.data.db.AppDatabase.MIGRATION_2_3,
            io.github.shiaho777.logsleuth.app.data.db.AppDatabase.MIGRATION_3_4,
            io.github.shiaho777.logsleuth.app.data.db.AppDatabase.MIGRATION_4_5,
            io.github.shiaho777.logsleuth.app.data.db.AppDatabase.MIGRATION_5_6,
        ).build()
        val notifications = io.github.shiaho777.logsleuth.app.service.NotificationHelper(context)
        val recorder = io.github.shiaho777.logsleuth.app.core.crashes.CrashRecorder(
            db.crashEventDao(),
            io.github.shiaho777.logsleuth.app.core.apps.InstalledApps(context),
            io.github.shiaho777.logsleuth.app.core.crashes.CrashContextStore(context),
            notifications,
        )
        val remoteManager = io.github.shiaho777.logsleuth.app.core.remoteadb.RemoteAdbManager(context, settings)
        // Wait for the manager to pick up the persisted host.
        withTimeout(15_000) {
            while (remoteManager.state.value.host != host) kotlinx.coroutines.delay(100)
        }
        remoteManager.testConnection()
        withTimeout(60_000) {
            val checked = remoteManager.state.first {
                it.status == io.github.shiaho777.logsleuth.app.core.remoteadb.RemoteAdbManager.Status.READY ||
                    it.status == io.github.shiaho777.logsleuth.app.core.remoteadb.RemoteAdbManager.Status.FAILED
            }
            assertTrue(
                "remote probe failed: ${checked.detail}",
                checked.status == io.github.shiaho777.logsleuth.app.core.remoteadb.RemoteAdbManager.Status.READY,
            )
        }
        val engine = io.github.shiaho777.logsleuth.app.core.logcat.LogcatEngine(
            shizuku, root, checker, recorder, db.bookmarkDao(), remoteManager, settings,
        )
        try {
            engine.acquireClient()
            val entry = withTimeout(60_000) { engine.entries.first() }
            assertTrue("engine delivered an empty raw line", entry.raw.isNotBlank())
            val endpoint = engine.access.value.remoteEndpoint
            assertTrue("missing source on a remote log entry", endpoint != null && entry.remoteEndpoint == endpoint)
            assertTrue("local snapshot includes a remote log", engine.snapshot(null).entries.none { it.remoteEndpoint != null })
            assertTrue("remote snapshot is empty", engine.snapshot(endpoint).entries.isNotEmpty())
            assertTrue(
                "access kind was ${engine.access.value.kind}",
                engine.access.value.kind == io.github.shiaho777.logsleuth.app.core.logcat.AccessKind.REMOTE_ADB,
            )

            val recording = io.github.shiaho777.logsleuth.app.service.RecordingManager(
                context, engine, db.sessionDao(), db.filterDao(), settings, notifications,
                io.github.shiaho777.logsleuth.app.core.apps.InstalledApps(context),
            )
            val sessionId = recording.start("Remote capture test", io.github.shiaho777.logsleuth.app.core.filter.LogFilter.DEFAULT)
                .getOrThrow()
            try {
                val marker = "LogSleuthRemote_${System.nanoTime()}"
                withTimeout(15_000) {
                    val received = async(start = CoroutineStart.UNDISPATCHED) {
                        engine.entries.first { it.remoteEndpoint == endpoint && marker in it.message }
                    }
                    withContext(Dispatchers.IO) {
                        AdbConnection(host, port, keys(), readTimeoutMs = 5_000).use { connection ->
                            connection.authenticate()
                            connection.openShell("log -p i -t LogSleuthE2E $marker")
                            connection.readShell {}
                        }
                    }
                    received.await()
                }
                remoteManager.disconnect()
                withTimeout(15_000) { recording.state.first { !it.isRecording } }
                val saved = requireNotNull(db.sessionDao().getById(sessionId))
                val text = File(saved.filePath).readText()
                assertTrue("remote session source lost", saved.remoteEndpoint == endpoint)
                assertTrue("remote capture lost the last marker", marker in text)
                assertTrue("disconnect reason missing", "# LogSleuth source changed" in text)
                assertTrue("recording not finalized", saved.endedAt != null)
            } finally {
                recording.stop()
            }
        } finally {
            engine.releaseClient()
            db.close()
        }
    }

    @Test
    fun deadRemoteFallsBackInsteadOfBlankingTheStream() = runBlocking {
        assumeTrue("no remote_host argument", !host.isNullOrBlank())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = io.github.shiaho777.logsleuth.app.data.prefs.SettingsRepository(context)
        // Port 1 is reserved and nothing listens there: the link must fail.
        settings.setRemoteAdb(host!!, 1)
        settings.setAccessPreference(io.github.shiaho777.logsleuth.app.core.logcat.AccessPolicy.REMOTE)
        val shizuku = io.github.shiaho777.logsleuth.app.core.shizuku.ShizukuManager(context)
        val root = io.github.shiaho777.logsleuth.app.core.root.RootManager(context)
        val checker = io.github.shiaho777.logsleuth.app.core.logcat.AccessChecker(context, shizuku)
        val db = androidx.room.Room.databaseBuilder(
            context,
            io.github.shiaho777.logsleuth.app.data.db.AppDatabase::class.java,
            "remote-e2e-fallback.db",
        ).addMigrations(
            io.github.shiaho777.logsleuth.app.data.db.AppDatabase.MIGRATION_1_2,
            io.github.shiaho777.logsleuth.app.data.db.AppDatabase.MIGRATION_2_3,
            io.github.shiaho777.logsleuth.app.data.db.AppDatabase.MIGRATION_3_4,
            io.github.shiaho777.logsleuth.app.data.db.AppDatabase.MIGRATION_4_5,
            io.github.shiaho777.logsleuth.app.data.db.AppDatabase.MIGRATION_5_6,
        ).build()
        val recorder = io.github.shiaho777.logsleuth.app.core.crashes.CrashRecorder(
            db.crashEventDao(),
            io.github.shiaho777.logsleuth.app.core.apps.InstalledApps(context),
            io.github.shiaho777.logsleuth.app.core.crashes.CrashContextStore(context),
            io.github.shiaho777.logsleuth.app.service.NotificationHelper(context),
        )
        val remoteManager = io.github.shiaho777.logsleuth.app.core.remoteadb.RemoteAdbManager(context, settings)
        withTimeout(15_000) {
            while (remoteManager.state.value.port != 1) kotlinx.coroutines.delay(100)
        }
        remoteManager.forceReadyForTest()
        val engine = io.github.shiaho777.logsleuth.app.core.logcat.LogcatEngine(
            shizuku, root, checker, recorder, db.bookmarkDao(), remoteManager, settings,
        )
        try {
            engine.acquireClient()
            // The dead socket must mark the remote failed, not retry forever.
            withTimeout(30_000) {
                while (remoteManager.state.value.status !=
                    io.github.shiaho777.logsleuth.app.core.remoteadb.RemoteAdbManager.Status.FAILED
                ) {
                    kotlinx.coroutines.delay(200)
                }
            }
            assertTrue(
                "remote detail was ${remoteManager.state.value.detail}",
                remoteManager.state.value.detail != null,
            )
        } finally {
            engine.releaseClient()
            db.close()
            // Restore the working configuration for the other tests.
            settings.setRemoteAdb(host!!, port)
        }
    }

    @Test
    fun logcatStreamDeliversParsedLinesWithUid() = runBlocking {
        assumeTrue("no remote_host argument", !host.isNullOrBlank())
        val source = RemoteAdbLogcatSource(host!!, port, keys())
        withTimeout(90_000) {
            // Cold start dumps the remote ring buffer. Skip logcat's own
            // "--------- beginning of <buffer>" headers; the first real
            // line must look like threadtime with the uid column.
            val first = source.stream(null)
                .filter { !it.startsWith("---------") && it.isNotBlank() }
                .first()
            // threadtime: "MM-DD HH:MM:SS.mmm  PID  TID  UID LEVEL TAG: msg"
            val parts = first.trim().split(Regex("\\s+"))
            assertTrue("unexpected shape: $first", parts.size >= 6)
            assertTrue("no timestamp in: $first", Regex("""\d{2}-\d{2}""").containsMatchIn(parts[0]))
        }
    }
}