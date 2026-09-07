package io.github.shiaho777.logsleuth.app.core.logcat

import io.github.shiaho777.logsleuth.app.core.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
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
     */
    fun buildCommand(): List<String> = listOf("logcat", "-v", "threadtime", "-v", "uid")

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
    val job = launch(Dispatchers.IO) {
        try {
            while (isActive) {
                val line = reader.readLine() ?: break
                trySend(line)
            }
            close()
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
