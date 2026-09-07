package io.github.logsleuth.app.core.shizuku

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku

/** Where the app stands regarding the optional Shizuku integration. */
enum class ShizukuStatus {
    /** Shizuku app not installed (provider not found). */
    NOT_INSTALLED,

    /** Installed but service not running (or binder not received yet). */
    NOT_RUNNING,

    /** Running, but this app has not been granted permission yet. */
    PERMISSION_REQUIRED,

    /** Running and granted: shell commands available. */
    READY,
}

/**
 * Thin wrapper around the Shizuku client API: binder/permission listeners and
 * spawning remote processes as the shell user.
 */
class ShizukuManager(private val context: Context) {

    private val _status = MutableStateFlow(ShizukuStatus.NOT_RUNNING)
    val status: StateFlow<ShizukuStatus> = _status.asStateFlow()

    private var service: IShizukuService? = null

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDeadListener = Shizuku.OnBinderDeadListener { refresh() }
    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }

    fun isInstalled(): Boolean = try {
        context.packageManager.getProviderInfo(
            android.content.ComponentName(
                "moe.shizuku.privileged.api",
                "rikka.shizuku.ShizukuProvider",
            ),
            0,
        )
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    /** Registers listeners and computes the initial status. Call once at app start. */
    fun attach() {
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)
        refresh()
    }

    fun detach() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        service = null
    }

    fun refresh() {
        _status.value = computeStatus()
    }

    private fun computeStatus(): ShizukuStatus {
        if (!isInstalled()) return ShizukuStatus.NOT_INSTALLED
        if (!Shizuku.pingBinder()) {
            service = null
            return ShizukuStatus.NOT_RUNNING
        }
        val granted = try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
        return if (granted) ShizukuStatus.READY else ShizukuStatus.PERMISSION_REQUIRED
    }

    /** Shows the Shizuku permission dialog (no-op unless running). */
    fun requestPermission(requestCode: Int = REQUEST_CODE) {
        try {
            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(requestCode)
            }
        } catch (_: Exception) {
            // Old Shizuku versions throw on request; status stays PERMISSION_REQUIRED.
        }
        refresh()
    }

    /** Starts a process as the shell user (uid 2000). Caller must ensure READY. */
    fun newProcess(cmd: Array<String>): Process {
        val binder = Shizuku.getBinder() ?: error("Shizuku binder not available")
        val svc = service ?: IShizukuService.Stub.asInterface(binder).also { service = it }
        val remote: IRemoteProcess = svc.newProcess(cmd, null, null)
        return RemoteProcess(remote)
    }

    companion object {
        const val REQUEST_CODE = 1413
    }
}
