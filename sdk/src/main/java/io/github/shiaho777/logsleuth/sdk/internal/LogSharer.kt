package io.github.shiaho777.logsleuth.sdk.internal

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Builds the shareable zip and launches the system share sheet.
 *
 * Layout matches the viewer app's own session export so both import paths
 * behave the same: `logs/` entries in chronological order, `crash.txt`,
 * `device.txt`, `meta.txt`.
 */
internal object LogSharer {

    fun buildZip(context: Context, store: LogFileStore): File {
        val outDir = File(context.cacheDir, "sleuth").apply { mkdirs() }
        val staging = File(outDir, "staging")
        val (logs, crash) = store.snapshotInto(staging)

        val out = File(outDir, "logsleuth_report.zip")
        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            logs.forEach { file ->
                zip.putNextEntry(ZipEntry("logs/${file.name}"))
                FileInputStream(file).use { it.copyTo(zip) }
                zip.closeEntry()
            }
            crash?.let {
                zip.putNextEntry(ZipEntry("crash.txt"))
                FileInputStream(it).use { f -> f.copyTo(zip) }
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("device.txt"))
            zip.write(deviceInfo(context).toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("meta.txt"))
            zip.write(meta(context, logs.size).toByteArray())
            zip.closeEntry()
        }
        staging.deleteRecursively()
        return out
    }

    fun share(context: Context, zip: File) {
        val authority = context.packageName + ".sleuth.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, zip)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "LogSleuth report (${context.packageName})")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "Share logs")
        if (context !is android.app.Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        // startActivity must be called from the main thread; the SDK's writer
        // executor is a background thread. Hop over via the main looper.
        val appContext = context.applicationContext
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        main.post { appContext.startActivity(chooser) }
    }

    private fun deviceInfo(context: Context): String = buildString {
        appendLine("manufacturer=${Build.MANUFACTURER}")
        appendLine("model=${Build.MODEL}")
        appendLine("device=${Build.DEVICE}")
        appendLine("android=${Build.VERSION.RELEASE} (sdk ${Build.VERSION.SDK_INT})")
        appendLine("app=${context.packageName} ${appVersion(context)}")
    }

    private fun meta(context: Context, fileCount: Int): String = buildString {
        appendLine("source=sdk")
        appendLine("app=${context.packageName}")
        appendLine("generatedAt=${System.currentTimeMillis()}")
        appendLine("logFiles=$fileCount")
    }

    private fun appVersion(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    }.getOrDefault("?")
}
