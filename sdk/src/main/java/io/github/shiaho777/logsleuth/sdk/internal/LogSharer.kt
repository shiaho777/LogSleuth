package io.github.shiaho777.logsleuth.sdk.internal

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Builds the shareable zip and launches the system share sheet. */
internal object LogSharer {

    fun buildZip(context: Context, store: LogFileStore): File {
        val outDir = File(context.cacheDir, "sleuth").apply { mkdirs() }
        val out = File(outDir, "logsleuth_report.zip")
        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            store.logFiles().sortedBy { it.name }.forEach { file ->
                zip.putNextEntry(ZipEntry("logs/${file.name}"))
                FileInputStream(file).use { it.copyTo(zip) }
                zip.closeEntry()
            }
            store.crashFile().takeIf { it.exists() }?.let { crash ->
                zip.putNextEntry(ZipEntry("crash.txt"))
                FileInputStream(crash).use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
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
}
