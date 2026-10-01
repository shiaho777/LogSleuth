package io.github.shiaho777.logsleuth.app.core.shizuku

import android.os.ParcelFileDescriptor
import moe.shizuku.server.IRemoteProcess
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

/**
 * Local java.lang.Process wrapper around the AIDL [IRemoteProcess] returned by
 * `IShizukuService.newProcess`. The Shizuku API artifact ships an equivalent
 * class with a package-private constructor, so we provide our own.
 *
 * The AIDL getters mirror java.lang.Process naming: `remote.inputStream` is
 * the pipe the *client* reads (remote stdout), `remote.outputStream` the one
 * the client writes (remote stdin). The streams are lazily bound and cached —
 * each binder call dups a fresh fd, so repeated calls would leak one.
 */
class RemoteProcess(private val remote: IRemoteProcess) : Process() {

    private var input: InputStream? = null
    private var output: OutputStream? = null
    private var error: InputStream? = null

    /** stdin of the remote process. */
    override fun getOutputStream(): OutputStream =
        output ?: ParcelFileDescriptor.AutoCloseOutputStream(remote.outputStream).also { output = it }

    /** stdout of the remote process. */
    override fun getInputStream(): InputStream =
        input ?: ParcelFileDescriptor.AutoCloseInputStream(remote.inputStream).also { input = it }

    override fun getErrorStream(): InputStream =
        error ?: ParcelFileDescriptor.AutoCloseInputStream(remote.errorStream).also { error = it }

    override fun waitFor(): Int = remote.waitFor()

    override fun waitFor(timeout: Long, unit: TimeUnit): Boolean =
        remote.waitForTimeout(timeout, unit.name)

    override fun exitValue(): Int = remote.exitValue()

    override fun destroy() {
        remote.destroy()
    }

    fun isAliveCompat(): Boolean = remote.alive()
}
