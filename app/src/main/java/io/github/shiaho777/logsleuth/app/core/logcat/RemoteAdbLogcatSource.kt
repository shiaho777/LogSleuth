package io.github.shiaho777.logsleuth.app.core.logcat

import io.github.shiaho777.logsleuth.app.core.remoteadb.AdbConnection
import io.github.shiaho777.logsleuth.app.core.remoteadb.AdbKeyManager
import io.github.shiaho777.logsleuth.app.core.remoteadb.RemoteLogLines
import java.io.EOFException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RemoteAdbLogcatSource(
    private val host: String,
    private val port: Int,
    private val keys: AdbKeyManager,
    internal val packages: Map<Int, String> = emptyMap(),
) : LogcatSource {
    override fun stream(since: String?): Flow<String> = callbackFlow {
        val connection = AdbConnection(host, port, keys)
        val job = launch {
            try {
                connection.authenticate()
                connection.openShell(buildCommand(since).joinToString(" "))
                val lines = RemoteLogLines { trySendBlocking(it) }
                connection.readShell(lines::append)
                if (isActive) {
                    lines.flush()
                    close(EOFException("Remote logcat stream ended"))
                }
            } catch (failure: Exception) {
                if (isActive) close(failure)
            }
        }
        awaitClose {
            connection.close()
            job.cancel()
        }
    }.flowOn(Dispatchers.IO)
}
