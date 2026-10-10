package io.github.shiaho777.logsleuth.app.core.logcat

import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import io.github.shiaho777.logsleuth.app.core.root.toShellCommand
import java.io.IOException
import java.util.concurrent.Executor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs logcat in its own root shell. The main shell stays free for the grant
 * check; this one lives only as long as the collector does.
 *
 * `su -c` is not used. Some root managers reject it, and a stamp passed to
 * `-T` contains a space. libsu feeds a real shell, and [toShellCommand]
 * quotes each argument.
 */
class RootLogcatSource : LogcatSource {

    override fun stream(since: String?): Flow<String> = callbackFlow {
        val shell = try {
            withContext(Dispatchers.IO) {
                Shell.Builder.create().setTimeout(20).build()
            }
        } catch (t: Throwable) {
            close(t)
            return@callbackFlow
        }
        if (!shell.isRoot) {
            runCatching { shell.close() }
            close(RootDeniedException())
            return@callbackFlow
        }

        val stderr = ArrayDeque<String>(4)
        val stdout = ShellLines { line ->
            // The channel is closed once the collector leaves. A late line
            // from the shell thread must not crash that thread.
            runCatching { trySendBlocking(line) }
        }
        val err = ShellLines { line ->
            if (stderr.size == 3) stderr.removeFirst()
            stderr.addLast(line)
        }
        val future = shell.newJob()
            .add(toShellCommand(buildCommand(since)))
            .to(stdout, err)
            .enqueue()
        val waiter = launch(Dispatchers.IO) {
            val result = runCatching { future.get() }.getOrNull()
            if (!isActive) return@launch
            val code = result?.code ?: -1
            if (code == 0) close() else close(LogcatDiedException(code, stderr.toList()))
        }
        awaitClose {
            waiter.cancel()
            future.cancel(true)
            // close() destroys the process. waitAndClose() would block forever
            // on a logcat that never exits.
            runCatching { shell.close() }
        }
    }
}

/** libsu fell back to `sh`. The engine should stop using the root path. */
class RootDeniedException : IOException("root was not granted")

/**
 * [CallbackList]'s executor constructors are protected; the default one posts
 * to the main thread, which a live logcat must not do.
 */
private class ShellLines(
    private val onLine: (String) -> Unit,
) : CallbackList<String>(Executor { it.run() }) {
    override fun onAddElement(e: String) = onLine(e)
}
