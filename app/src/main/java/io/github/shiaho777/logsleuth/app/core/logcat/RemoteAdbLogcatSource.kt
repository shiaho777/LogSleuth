package io.github.shiaho777.logsleuth.app.core.logcat

import io.github.shiaho777.logsleuth.app.core.remoteadb.AdbConnection
import io.github.shiaho777.logsleuth.app.core.remoteadb.AdbKeyManager
import java.io.EOFException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Streams logcat from a *second* device over wireless ADB. The remote adbd
 * runs the shell as uid 2000, so the stream carries the uid column and the
 * full buffer set — same shape as the Shizuku path, just over TCP.
 *
 * One connection per stream; the engine's reconnect/backoff applies when
 * the socket dies. Cancellation closes the socket, which unblocks the
 * reader thread.
 */
class RemoteAdbLogcatSource(
    private val host: String,
    private val port: Int,
    private val keys: AdbKeyManager,
) : LogcatSource {

    override fun stream(since: String?): Flow<String> = callbackFlow {
        val connection = AdbConnection(host, port, keys)
        val job = launch(Dispatchers.IO) {
            try {
                connection.authenticate()
                connection.openShell(buildCommand(since).joinToString(" "))
                val pending = StringBuilder()
                connection.readShell { chunk ->
                    pending.append(String(chunk, Charsets.UTF_8))
                    var nl = pending.indexOf("\n")
                    while (nl >= 0) {
                        val line = pending.substring(0, nl).trimEnd('\r')
                        pending.delete(0, nl + 1)
                        if (line.isNotEmpty()) trySendBlocking(line)
                        nl = pending.indexOf("\n")
                    }
                }
                if (isActive) close()
            } catch (e: EOFException) {
                if (isActive) close() else close()
            } catch (t: Throwable) {
                if (isActive) close(t) else close()
            }
        }
        awaitClose {
            job.cancel()
            connection.close()
        }
    }
}