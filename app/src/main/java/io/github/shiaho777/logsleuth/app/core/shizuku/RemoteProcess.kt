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
 */
class RemoteProcess(private val remote: IRemoteProcess) : Process() {

    /** stdin of the remote process. */
    override fun getOutputStream(): OutputStream =
        ParcelFileDescriptor.AutoCloseOutputStream(remote.inputStream)

    /** stdout of the remote process. */
    override fun getInputStream(): InputStream =
        ParcelFileDescriptor.AutoCloseInputStream(remote.outputStream)

    override fun getErrorStream(): InputStream =
        ParcelFileDescriptor.AutoCloseInputStream(remote.errorStream)

    override fun waitFor(): Int = remote.waitFor()

    override fun exitValue(): Int = remote.exitValue()

    override fun destroy() {
        remote.destroy()
    }

    fun waitForTimeout(timeout: Long, unit: TimeUnit): Boolean =
        remote.waitForTimeout(timeout, unit.name)

    fun isAliveCompat(): Boolean = remote.alive()
}
