package io.github.shiaho777.logsleuth.app.core.importer

import android.content.Context
import android.net.Uri
import io.github.shiaho777.logsleuth.app.core.logcat.LogcatParser
import io.github.shiaho777.logsleuth.app.core.logcat.ParsedLine
import io.github.shiaho777.logsleuth.app.data.db.SessionDao
import io.github.shiaho777.logsleuth.app.data.db.SessionEntity
import java.io.BufferedWriter
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Imports log files shared into the app: plain `.log/.txt` files and zip
 * bundles produced by logsleuth-sdk or by our own session export.
 *
 * Everything streams — the source is never fully loaded into memory, and
 * [ImportCore.MAX_IMPORT_BYTES] caps uncompressed size as a zip-bomb guard.
 * A zip's `logs/` entries are merged in filename order (SDK ring snapshots
 * are zero-padded so lexical order is chronological); `meta.txt` fields
 * carry over to the session row.
 */
@Singleton
class LogImporter @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val sessionDao: SessionDao,
) {
    private class Counter {
        var bytes = 0L
        var entries = 0L
    }

    suspend fun import(uri: Uri): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            pruneStalePartDirs()
            val displayName = queryDisplayName(uri) ?: "import"
            val dir = File(context.filesDir, "imports").apply { mkdirs() }
            val file = File(dir, "import_${System.currentTimeMillis()}.log")
            val counter = Counter()

            try {
                val meta = if (sniffIsZip(uri)) {
                    importZip(uri, file, counter)
                } else {
                    open(uri)?.use { ins -> copyCounted(ins, file, counter) }
                        ?: error("Cannot read $uri")
                    null
                }
                require(counter.bytes > 0) { "Empty file" }

                sessionDao.insert(
                    SessionEntity(
                        name = meta?.name ?: displayName.substringBeforeLast('.'),
                        filePath = file.absolutePath,
                        startedAt = meta?.startedAt ?: System.currentTimeMillis(),
                        endedAt = System.currentTimeMillis(),
                        lineCount = counter.entries,
                        accessKind = "IMPORT",
                        imported = true,
                    ),
                )
            } catch (t: Throwable) {
                file.delete()
                throw t
            }
        }
    }

    private fun open(uri: Uri): InputStream? =
        context.contentResolver.openInputStream(uri)

    private fun sniffIsZip(uri: Uri): Boolean =
        open(uri)?.use { ins ->
            val head = ByteArray(4)
            ImportCore.isZip(head.copyOf(ins.read(head)))
        } ?: false

    /**
     * Writes every line of [ins] to [file], counting bytes (against the cap)
     * and parsed log entries (for the session's line count).
     */
    private fun copyCounted(ins: InputStream, file: File, counter: Counter) {
        file.bufferedWriter(Charsets.UTF_8).use { out ->
            ins.bufferedReader(Charsets.UTF_8).forEachLine { line ->
                counter.bytes += line.toByteArray(Charsets.UTF_8).size + 1
                require(counter.bytes <= ImportCore.MAX_IMPORT_BYTES) { "Import too large" }
                if (line.isNotBlank() && line[0] != '#' &&
                    LogcatParser.parse(line) is ParsedLine.Entry
                ) {
                    counter.entries++
                }
                out.write(line)
                out.newLine()
            }
        }
    }

    /**
     * Merges a zip bundle in a single decompression pass: wanted entries are
     * extracted to part files, then concatenated in plan order. [counter]
     * tracks uncompressed bytes during extraction (zip-bomb guard) and parsed
     * entries during the merge.
     */
    private fun importZip(uri: Uri, file: File, counter: Counter): ImportCore.Meta? {
        var meta: ImportCore.Meta? = null
        val names = mutableListOf<String>()

        open(uri)?.use { ins ->
            ZipInputStream(ins).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    names += e.name
                    if (e.name.substringAfterLast('/') == "meta.txt") {
                        meta = runCatching {
                            ImportCore.parseMeta(readCapped(z, 64 * 1024).toString(Charsets.UTF_8))
                        }.getOrNull()
                    }
                    z.closeEntry()
                }
            }
        } ?: error("Cannot read $uri")

        val plan = ImportCore.planZip(names)
        require(plan.logEntries.isNotEmpty() || plan.crashEntry != null) {
            "No log file inside zip"
        }

        val partsDir = File(context.cacheDir, "import_${System.currentTimeMillis()}")
        val parts = HashMap<String, File>()
        try {
            val wanted = (plan.logEntries + listOfNotNull(plan.crashEntry)).toSet()
            open(uri)?.use { ins ->
                ZipInputStream(ins).use { z ->
                    var i = 0
                    while (true) {
                        val e = z.nextEntry ?: break
                        if (e.name in wanted) {
                            val part = File(partsDir.apply { mkdirs() }, "part_${i++}.part")
                            parts[e.name] = part
                            part.outputStream().use { pout ->
                                val buf = ByteArray(8192)
                                while (true) {
                                    val n = z.read(buf)
                                    if (n <= 0) break
                                    counter.bytes += n
                                    require(counter.bytes <= ImportCore.MAX_IMPORT_BYTES) {
                                        "Import too large"
                                    }
                                    pout.write(buf, 0, n)
                                }
                            }
                        }
                        z.closeEntry()
                    }
                }
            }

            file.bufferedWriter(Charsets.UTF_8).use { out ->
                for (name in plan.logEntries + listOfNotNull(plan.crashEntry)) {
                    val part = parts[name]
                        ?: throw java.io.IOException("Missing entry $name")
                    part.bufferedReader(Charsets.UTF_8).forEachLine { line ->
                        if (line.isNotBlank() && line[0] != '#' &&
                            LogcatParser.parse(line) is ParsedLine.Entry
                        ) {
                            counter.entries++
                        }
                        out.write(line)
                        out.newLine()
                    }
                }
            }
        } finally {
            partsDir.deleteRecursively()
        }
        return meta
    }

    /**
     * A process death mid-import can leave `import_*` part dirs behind —
     * `partsDir.deleteRecursively()` only runs on a live VM. Stale ones are
     * safe to remove after a day (an in-flight import is never that old).
     */
    private fun pruneStalePartDirs() {
        runCatching {
            val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
            context.cacheDir.listFiles()
                ?.filter { it.isDirectory && it.name.startsWith("import_") && it.lastModified() < cutoff }
                ?.forEach { it.deleteRecursively() }
        }
    }

    private fun readCapped(ins: InputStream, max: Int): ByteArray {
        val buf = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        var total = 0
        while (true) {
            val n = ins.read(chunk, 0, minOf(chunk.size, max - total))
            if (n <= 0) break
            total += n
            buf.write(chunk, 0, n)
        }
        return buf.toByteArray()
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    }.getOrNull()
}
