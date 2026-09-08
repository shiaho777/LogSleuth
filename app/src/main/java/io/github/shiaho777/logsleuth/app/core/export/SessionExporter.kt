package io.github.shiaho777.logsleuth.app.core.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import io.github.shiaho777.logsleuth.app.R
import io.github.shiaho777.logsleuth.app.data.db.SessionDao
import io.github.shiaho777.logsleuth.app.data.db.SessionEntity
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
) {
    companion object {
        const val AUTHORITY_SUFFIX = ".fileprovider"
    }

    enum class Format { TXT, ZIP }

    suspend fun export(sessionId: Long, format: Format): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
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
                    ZipOutputStream(out.outputStream().buffered()).use { zip ->
                        zip.putNextEntry(ZipEntry("logs/session.log"))
                        FileInputStream(source).use { it.copyTo(zip) }
                        zip.closeEntry()

                        zip.putNextEntry(ZipEntry("device.txt"))
                        zip.write(deviceInfo().toByteArray())
                        zip.closeEntry()

                        zip.putNextEntry(ZipEntry("meta.txt"))
                        zip.write(sessionMeta(session).toByteArray())
                        zip.closeEntry()
                    }
                    out
                }
            }
        }
    }

    /** Shares an arbitrary text payload (e.g. a crash snippet) as a file. */
    suspend fun shareSnippet(name: String, content: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
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

    private fun deviceInfo(): String = buildString {
        appendLine("manufacturer=${android.os.Build.MANUFACTURER}")
        appendLine("model=${android.os.Build.MODEL}")
        appendLine("device=${android.os.Build.DEVICE}")
        appendLine("android=${android.os.Build.VERSION.RELEASE} (sdk ${android.os.Build.VERSION.SDK_INT})")
        appendLine("app=${context.packageName} ${context.packageManager.getPackageInfo(context.packageName, 0).versionName}")
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
