package io.github.shiaho777.logsleuth.app.core.importer

/**
 * Pure helpers for [LogImporter]: which zip entries to merge, how to read
 * `meta.txt`, and import size limits. Kept Android-free so it is unit-testable.
 */
object ImportCore {

    /** Refuse imports above this uncompressed size — zip-bomb guard. */
    const val MAX_IMPORT_BYTES = 256L * 1024 * 1024

    /** Zip local-file magic (`PK\x03\x04`) sniffed from the first bytes. */
    fun isZip(header: ByteArray): Boolean =
        header.size >= 2 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()

    /**
     * Which zip entries to merge into the imported log, in order.
     *
     * - Entries under `logs/` win (viewer and SDK bundles); sorted by name —
     *   SDK ring snapshots are zero-padded (`log_00.log`…) so lexical order
     *   is chronological order.
     * - Otherwise fall back to every `.log`/`.txt` except metadata files.
     * - `crash.txt` (SDK crash report) is appended after the logs, not merged.
     */
    data class ZipPlan(val logEntries: List<String>, val crashEntry: String?)

    fun planZip(entryNames: List<String>): ZipPlan {
        val crash = entryNames.firstOrNull { it.substringAfterLast('/') == "crash.txt" }
        val candidates = entryNames.filter { name ->
            val base = name.substringAfterLast('/')
            (base.endsWith(".log") || base.endsWith(".txt")) &&
                base != "meta.txt" && base != "device.txt" && base != "crash.txt"
        }
        val underLogs = candidates.filter { it.startsWith("logs/") }
        val logs = (underLogs.ifEmpty { candidates }).sorted()
        return ZipPlan(logs, crash)
    }

    /** Parsed `meta.txt` (key=value lines) from a viewer or SDK bundle. */
    data class Meta(
        val name: String? = null,
        val startedAt: Long? = null,
        val fromSdk: Boolean = false,
    )

    fun parseMeta(text: String): Meta {
        val map = text.lineSequence()
            .mapNotNull { line ->
                val eq = line.indexOf('=')
                if (eq <= 0) null else line.substring(0, eq).trim() to line.substring(eq + 1).trim()
            }
            .toMap()
        // Note: meta `app`/`filterPackage` must NOT seed the session's
        // package filter — SDK captures carry no uid column, so a package
        // filter would match nothing on replay.
        return Meta(
            name = map["name"]?.takeIf { it.isNotBlank() },
            startedAt = map["startedAt"]?.toLongOrNull(),
            fromSdk = map["source"] == "sdk" || map.containsKey("logFiles"),
        )
    }
}
