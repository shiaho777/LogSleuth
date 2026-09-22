package io.github.shiaho777.logsleuth.sdk.internal

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import io.github.shiaho777.logsleuth.sdk.SleuthConfig
import java.io.File

/**
 * Ring-buffer file store. Pure file IO once constructed — the primary
 * constructor takes the directory and an app-label lambda so it stays
 * unit-testable off-device; Android callers go through [forContext].
 */
internal class LogFileStore(
    val dir: File,
    private val appLabel: () -> String,
    private val config: SleuthConfig,
) {
    init {
        dir.mkdirs()
    }

    private var currentFile: File? = null
    private var currentSize = 0L

    @Synchronized
    fun writeHeader() {
        rotateIfNeeded(forceNew = true)
        // Routed through appendBlock so the header counts toward currentSize.
        appendBlock(deviceHeader())
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
        // Keep line breaks: crash stack traces are unreadable on one line.
        // Redaction is applied per-line via append().
        text.split('\n').forEachIndexed { i, line ->
            if (i > 0) appendRaw("\n")
            if (line.isNotEmpty()) append(line)
        }
    }

    private fun appendRaw(s: String) {
        val bytes = s.toByteArray(Charsets.UTF_8)
        currentFile?.appendBytes(bytes)
        currentSize += bytes.size
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

        val file = File(dir, "log_%d.log".format(nextSlot()))
        file.writeText("")
        currentFile = file
        currentSize = 0L
        return file
    }

    /**
     * Next ring slot: advances past the current file, so a full ring wraps
     * 0→1→2→0→…; on a fresh store (e.g. new process over leftover files)
     * fills a free slot first, otherwise overwrites the oldest file.
     * `(maxIndex + 1) % maxFiles` would degenerate to slot 0 forever once the
     * ring is full — that is not a ring.
     */
    private fun nextSlot(): Int {
        val current = currentFile
        if (current != null) return (current.indexOf() + 1) % config.maxFiles
        val files = logFilesByIndex()
        if (files.isEmpty()) return 0
        val used = files.map { it.indexOf() }.toSet()
        val free = (0 until config.maxFiles).firstOrNull { it !in used }
        return free ?: files.minByOrNull { it.lastModified() }!!.indexOf()
    }

    private fun File.indexOf(): Int =
        name.removePrefix("log_").removeSuffix(".log").toIntOrNull() ?: 0

    /** Ring slot order — used only for rotation bookkeeping. */
    private fun logFilesByIndex(): List<File> =
        dir.listFiles { f -> f.name.startsWith("log_") && f.name.endsWith(".log") }
            ?.sortedBy { it.indexOf() }
            .orEmpty()

    /**
     * All ring files, oldest write first. mtime is the chronological truth:
     * after the ring wraps, log_0 can hold the *newest* content, so neither
     * index order nor name order is correct.
     */
    fun logFiles(): List<File> =
        logFilesByIndex().sortedWith(compareBy({ it.lastModified() }, { it.indexOf() }))

    fun crashFile(): File = File(dir, "last_crash.txt")

    fun pendingCrashFlag(): File = File(dir, "crash_pending")

    /**
     * Copies every ring file (chronological order) and the crash report into
     * [destDir] while holding the store lock, so a share snapshot can never
     * observe a file mid-append. Copied names are re-indexed sequentially so
     * alphabetical order in the zip equals chronological order.
     */
    @Synchronized
    fun snapshotInto(destDir: File): Pair<List<File>, File?> {
        destDir.listFiles()?.forEach { it.delete() }
        destDir.mkdirs()
        val logs = logFiles().mapIndexed { i, src ->
            val dst = File(destDir, "log_%02d.log".format(i))
            src.copyTo(dst, overwrite = true)
            dst
        }
        val crash = crashFile().takeIf { it.exists() }
            ?.let { it.copyTo(File(destDir, "crash.txt"), overwrite = true) }
        return logs to crash
    }

    private fun deviceHeader(): String = buildString {
        appendLine("==== LogSleuth SDK session ====")
        appendLine("time=${System.currentTimeMillis()}")
        appendLine("app=${appLabel()}")
        appendLine("device=${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
        appendLine("android=${Build.VERSION.RELEASE} (sdk ${Build.VERSION.SDK_INT})")
        appendLine("pid=${android.os.Process.myPid()}")
        appendLine("================================")
    }

    companion object {
        fun forContext(context: Context, config: SleuthConfig): LogFileStore =
            LogFileStore(
                dir = File(context.filesDir, "sleuth"),
                appLabel = { "${context.packageName} ${appVersion(context)}" },
                config = config,
            )

        private fun appVersion(context: Context): String = runCatching {
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
}
