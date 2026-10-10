package io.github.shiaho777.logsleuth.app.core.root

import android.content.Context
import com.topjohnwu.superuser.Shell
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Whether the user has a working root shell for this process. */
enum class RootStatus {
    /** The user has not asked for root. We do not exec `su`. */
    UNKNOWN,

    /** `su` is opening, which may be showing the manager's grant dialog. */
    CHECKING,

    /** A root shell came up. Magisk, KernelSU, and APatch all land here. */
    READY,

    /** `su` ran and the shell was not uid 0, or it could not be created. */
    DENIED,
}

/**
 * Opt-in root access via libsu. Nothing here runs until [refresh] is called
 * with `enabled = true`, so an unrooted phone never sees a grant dialog.
 */
class RootManager(context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val generation = AtomicInteger()

    private val _status = MutableStateFlow(RootStatus.UNKNOWN)
    val status: StateFlow<RootStatus> = _status.asStateFlow()

    init {
        // Configure the main shell before the first getShell(). This does not
        // start a process.
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setContext(context.applicationContext)
                .setTimeout(20),
        )
    }

    /**
     * Opens or closes the main root shell to match [enabled]. Safe to call
     * again; a newer call cancels the result of an older one.
     */
    fun refresh(enabled: Boolean) {
        val gen = generation.incrementAndGet()
        if (!enabled) {
            _status.value = RootStatus.UNKNOWN
            runCatching { Shell.getCachedShell()?.close() }
            return
        }
        val cached = Shell.getCachedShell()
        if (cached != null && cached.isRoot) {
            _status.value = RootStatus.READY
            return
        }
        // A cached `sh` fallback would make the next getShell() skip the
        // grant dialog. Drop it so a retry can ask again.
        if (cached != null) runCatching { cached.close() }
        _status.value = RootStatus.CHECKING
        scope.launch {
            val shell = runCatching { Shell.getShell() }.getOrNull()
            val rooted = shell?.isRoot == true
            if (generation.get() != gen) {
                // This call lost. If root is no longer wanted, drop the shell
                // it may have just cached.
                if (_status.value != RootStatus.READY && _status.value != RootStatus.CHECKING) {
                    runCatching { Shell.getCachedShell()?.close() }
                }
                return@launch
            }
            // A denied `su` falls back to `sh` and stays cached. Close it so
            // the next opt-in can ask again, and so we don't keep a shell
            // the user does not have root on.
            if (!rooted) runCatching { shell?.close() }
            _status.value = if (rooted) RootStatus.READY else RootStatus.DENIED
        }
    }

    /**
     * The dedicated logcat shell came up as plain `sh`. Stop treating root as
     * usable so the engine does not prompt again on every reconnect.
     */
    fun markDenied() {
        generation.incrementAndGet()
        _status.value = RootStatus.DENIED
        runCatching { Shell.getCachedShell()?.close() }
    }
}
