package io.github.shiaho777.logsleuth.app.core.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import io.github.shiaho777.logsleuth.app.R
import io.github.shiaho777.logsleuth.app.data.db.CrashEventEntity
import io.github.shiaho777.logsleuth.app.data.db.SessionDao
import io.github.shiaho777.logsleuth.app.data.db.SessionEntity
import io.github.shiaho777.logsleuth.app.data.prefs.SettingsRepository
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Packages a recorded session as a shareable file:
 *  - txt: the raw log file, or
 *  - zip: log + device info + metadata (mirrors the SDK export layout).
 */
@Singleton
class SessionExporter @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val sessionDao: SessionDao,
    private val settingsRepository: SettingsRepository,
) {
    companion object {
        const val AUTHORITY_SUFFIX = ".fileprovider"

        /** Shared exports outlive the share sheet by a day, then get pruned. */
        private const val EXPORT_TTL_MS = 24L * 60 * 60 * 1000
    }

    enum class Format { TXT, ZIP }

    suspend fun export(
        sessionId: Long,
        format: Format,
        includeDevice: Boolean? = null,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            pruneExports()
            val session = sessionDao.getById(sessionId) ?: error("Session $sessionId not found")
            val source = File(session.filePath)
            require(source.exists()) { "Log file missing: ${session.filePath}" }

            when (format) {
                Format.TXT -> {
                    val out = exportFile(session, "txt")
                    source.copyTo(out, overwrite = true)
                    out
                }

                Format.ZIP -> {
                    val out = exportFile(session, "zip")
                    val withDevice = includeDevice
                        ?: settingsRepository.settings.first().includeDeviceInfoInZip
                    ZipOutputStream(out.outputStream().buffered()).use { zip ->
                        zip.putNextEntry(ZipEntry("logs/session.log"))
                        FileInputStream(source).use { it.copyTo(zip) }
                        zip.closeEntry()
                        if (withDevice) {
                            zip.putText("device.txt", deviceInfo())
                        }
                        zip.putText("meta.txt", sessionMeta(session))
                    }
                    out
                }
            }
        }
    }

    /**
     * Zip of one crash: the crashed app's version, this device, the stack,
     * and the surrounding lines when they were saved.
     */
    suspend fun shareCrash(
        crash: CrashEventEntity,
        includeApp: Boolean? = null,
        includeDevice: Boolean? = null,
    ): Result<Unit> = crashZip(crash, includeApp, includeDevice).fold(
        onSuccess = { file ->
            share(file, "application/zip")
            Result.success(Unit)
        },
        onFailure = { Result.failure(it) },
    )

    suspend fun crashZip(
        crash: CrashEventEntity,
        includeApp: Boolean? = null,
        includeDevice: Boolean? = null,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            pruneExports()
            val flags = settingsRepository.settings.first()
            val withApp = includeApp ?: flags.includeAppInfoInZip
            val withDevice = includeDevice ?: flags.includeDeviceInfoInZip
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val safe = (crash.packageName ?: "unknown").replace(Regex("""[^\w.-]+"""), "_")
            val out = File(dir, "crash_${safe}_${crash.id}.zip")
            ZipOutputStream(out.outputStream().buffered()).use { zip ->
                if (withApp) zip.putText("app.txt", appInfo(crash))
                if (withDevice) zip.putText("device.txt", deviceInfo())
                zip.putText("crash.txt", crashBody(crash))
                val contextFile = crash.contextPath?.let { File(it) }?.takeIf { it.isFile }
                if (contextFile != null) {
                    zip.putNextEntry(ZipEntry("context.log"))
                    contextFile.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            out
        }
    }

    suspend fun saveToUri(file: File, uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            } ?: error("Could not open $uri")
            Unit
        }
    }

    /** Shares an arbitrary text payload (e.g. a crash snippet) as a file. */
    suspend fun shareSnippet(name: String, content: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                pruneExports()
                val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                val file = File(dir, name)
                file.writeText(content)
                share(file, "text/plain")
            }
        }

    /** Launches the system share sheet for an exported file. */
    fun share(file: File, mime: String) {
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + AUTHORITY_SUFFIX,
            file,
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, context.getString(R.string.share_logs))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun exportFile(session: SessionEntity, ext: String): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safeName = session.name.replace(Regex("""[^\w.-]+"""), "_")
        return File(dir, "${safeName}_${session.id}.$ext")
    }

    /**
     * Removes exports older than [EXPORT_TTL_MS]. The TTL is generous on
     * purpose: a just-shared file may still be read by the receiving app
     * through the FileProvider grant.
     */
    private fun pruneExports() {
        runCatching {
            val cutoff = System.currentTimeMillis() - EXPORT_TTL_MS
            File(context.cacheDir, "exports").listFiles()
                ?.filter { it.lastModified() < cutoff }
                ?.forEach { it.delete() }
        }
    }

    private fun deviceInfo(): String = buildString {
        appendLine("manufacturer=${android.os.Build.MANUFACTURER}")
        appendLine("brand=${android.os.Build.BRAND}")
        appendLine("model=${android.os.Build.MODEL}")
        appendLine("device=${android.os.Build.DEVICE}")
        appendLine("product=${android.os.Build.PRODUCT}")
        appendLine("board=${android.os.Build.BOARD}")
        appendLine("abis=${android.os.Build.SUPPORTED_ABIS.joinToString()}")
        appendLine("android=${android.os.Build.VERSION.RELEASE} (sdk ${android.os.Build.VERSION.SDK_INT})")
        appendLine("app=${context.packageName} ${context.packageManager.getPackageInfo(context.packageName, 0).versionName}")
    }

    private fun appInfo(crash: CrashEventEntity): String = buildString {
        crash.appName?.let { appendLine("APP_NAME: $it") }
        appendLine("PACKAGE: ${crash.packageName ?: "unknown"}")
        crash.versionName?.let { appendLine("VERSION_NAME: $it") }
        crash.versionCode?.let { appendLine("VERSION_CODE: $it") }
    }

    private fun crashBody(crash: CrashEventEntity): String = buildString {
        appendLine("type=${crash.type}")
        appendLine("time=${crash.time}")
        appendLine("pid=${crash.pid}")
        appendLine()
        append(crash.snippet)
    }

    private fun ZipOutputStream.putText(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray())
        closeEntry()
    }

    private fun sessionMeta(session: SessionEntity): String = buildString {
        appendLine("name=${session.name}")
        appendLine("startedAt=${session.startedAt}")
        appendLine("endedAt=${session.endedAt ?: ""}")
        appendLine("lineCount=${session.lineCount}")
        appendLine("access=${session.accessKind}")
        appendLine("filterPackage=${session.filterPackage ?: ""}")
    }
}
