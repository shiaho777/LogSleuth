package io.github.shiaho777.logsleuth.app.core.logcat

import io.github.shiaho777.logsleuth.app.core.shizuku.ShizukuManager
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Spawns and reads a `logcat` process. */
sealed interface LogcatSource {

    /**
     * Command line for streaming with the uid column included. Per-app
     * filtering is done client-side against this column, which works on both
     * access paths (logd restricts the server-side `--uid` mask to shell).
     *
     * `crash` and `events` buffers are merged in so native crash dumps
     * (`Fatal signal`, tag DEBUG) and `am_anr` events reach CrashDetector —
     * the default main buffer alone carries neither.
     */
    fun buildCommand(): List<String> =
        listOf("logcat", "-b", "main", "-b", "crash", "-b", "events", "-v", "threadtime", "-v", "uid")

    /** Streams raw logcat lines until the flow collector is cancelled. */
    fun stream(): Flow<String>
}

/** Runs logcat in the app's own process — requires the READ_LOGS grant. */
class LocalLogcatSource : LogcatSource {

    override fun stream(): Flow<String> = streamProcess {
        ProcessBuilder(buildCommand()).redirectErrorStream(true).start()
    }
}

/** Runs logcat as the shell user through Shizuku. */
class ShizukuLogcatSource(private val shizukuManager: ShizukuManager) : LogcatSource {

    override fun stream(): Flow<String> = streamProcess {
        shizukuManager.newProcess(buildCommand().toTypedArray())
    }
}

private fun streamProcess(start: () -> Process): Flow<String> = callbackFlow {
    val process = try {
        start()
    } catch (t: Throwable) {
        close(t)
        return@callbackFlow
    }

    val reader = process.inputStream.bufferedReader()
    // A few tail lines: stderr is merged into stdout, so a fast death
    // ("Permission denied", an unsupported flag) leaves its reason here.
    val tail = ArrayDeque<String>(4)
    val job = launch(Dispatchers.IO) {
        try {
            while (isActive) {
                val line = reader.readLine() ?: break
                // Blocking send: under bursts the reader slows down and the
                // logcat pipe backs up instead of silently dropping lines.
                trySendBlocking(line)
                if (tail.size == 3) tail.removeFirst()
                tail.addLast(line)
            }
            // A logcat that exits instantly with an error otherwise reads
            // as clean EOF — surface the real cause instead. Skip the
            // check when the loop ended via cancellation: we destroyed
            // the process ourselves, so a nonzero exit is expected.
            if (!isActive) {
                close()
            } else {
                val exited = process.waitFor(300, TimeUnit.MILLISECONDS)
                if (!exited || process.exitValue() == 0) {
                    close()
                } else {
                    close(LogcatDiedException(process.exitValue(), tail.toList()))
                }
            }
        } catch (t: Throwable) {
            // Destroying the process closes the stream: that is a normal shutdown.
            if (isActive) close(t) else close()
        }
    }

    awaitClose {
        job.cancel()
        runCatching { reader.close() }
        runCatching { process.destroy() }
    }
}

/** logcat exited on its own with a nonzero code; [message] carries the
 *  merged-stderr tail (e.g. "Permission denied"). */
class LogcatDiedException(exitCode: Int, lastLines: List<String>) : java.io.IOException(
    "logcat exited $exitCode" +
        if (lastLines.isEmpty()) "" else ": ${lastLines.joinToString(" ⏎ ")}",
)
