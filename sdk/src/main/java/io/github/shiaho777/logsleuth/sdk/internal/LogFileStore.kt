package io.github.shiaho777.logsleuth.sdk.internal

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import io.github.shiaho777.logsleuth.sdk.SleuthConfig
import java.io.File

/** Ring-buffer file store under filesDir/sleuth/. Pure file IO, unit-testable. */
internal class LogFileStore(
    private val context: Context,
    private val config: SleuthConfig,
) {
    val dir: File by lazy { File(context.filesDir, "sleuth").apply { mkdirs() } }

    private var currentFile: File? = null
    private var currentSize = 0L

    @Synchronized
    fun writeHeader() {
        val file = rotateIfNeeded(forceNew = true)
        file.appendText(deviceHeader())
    }

    @Synchronized
    fun append(line: String) {
        val redacted = redact(line)
        val bytes = redacted.toByteArray(Charsets.UTF_8)
        val file = rotateIfNeeded(forceNew = false)
        file.appendBytes(bytes)
        currentSize += bytes.size
    }

    @Synchronized
    fun appendBlock(text: String) {
        text.split('\n').forEach { append(it) }
    }

    private fun redact(line: String): String {
        if (config.redactions.isEmpty()) return line
        var out = line
        for (pattern in config.redactions) {
            out = pattern.replace(out) { m -> m.value.take(4) + "***" }
        }
        return out
    }

    private fun rotateIfNeeded(forceNew: Boolean): File {
        val existing = currentFile
        if (!forceNew && existing != null && currentSize < config.maxFileBytes && existing.exists()) {
            return existing
        }

        val files = logFiles().sortedBy { it.name }
        val index = if (files.isEmpty()) 0 else (files.last().indexOf() + 1) % config.maxFiles
        val file = File(dir, "log_%d.log".format(index))

        // Overwrite semantics: keep only the newest (maxFiles) files.
        val all = (files + file).distinct().sortedBy { it.name }
        if (all.size > config.maxFiles) {
            all.dropLast(config.maxFiles).forEach { it.delete() }
        }

        file.writeText("")
        currentFile = file
        currentSize = 0L
        return file
    }

    private fun File.indexOf(): Int =
        name.removePrefix("log_").removeSuffix(".log").toIntOrNull() ?: 0

    fun logFiles(): List<File> =
        dir.listFiles { f -> f.name.startsWith("log_") && f.name.endsWith(".log") }
            ?.toList()
            .orEmpty()

    fun crashFile(): File = File(dir, "last_crash.txt")

    fun pendingCrashFlag(): File = File(dir, "crash_pending")

    private fun deviceHeader(): String = buildString {
        appendLine("==== LogSleuth SDK session ====")
        appendLine("time=${System.currentTimeMillis()}")
        appendLine("app=${context.packageName} ${appVersion()}")
        appendLine("device=${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
        appendLine("android=${Build.VERSION.RELEASE} (sdk ${Build.VERSION.SDK_INT})")
        appendLine("pid=${android.os.Process.myPid()}")
        appendLine("================================")
    }

    private fun appVersion(): String = runCatching {
        val info = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        info.versionName ?: "?"
    }.getOrDefault("?")
}
