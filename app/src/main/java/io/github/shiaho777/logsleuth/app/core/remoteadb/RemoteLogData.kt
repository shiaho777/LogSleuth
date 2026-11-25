package io.github.shiaho777.logsleuth.app.core.remoteadb

import java.io.ByteArrayOutputStream
import java.io.IOException

internal object RemotePackages {
    private val packageLine = Regex("""^package:([A-Za-z0-9_.]+)\s+uid:(\d+)$""")

    fun parse(lines: Sequence<String>): Map<Int, String> =
        lines.mapNotNull { line ->
            val match = packageLine.matchEntire(line.trim()) ?: return@mapNotNull null
            val uid = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
            uid to match.groupValues[1]
        }.groupBy({ it.first }, { it.second })
            .mapNotNull { (uid, packages) -> packages.distinct().singleOrNull()?.let { uid to it } }
            .toMap()
}

internal class RemoteLogLines(private val emit: (String) -> Unit) {
    private val pending = ByteArrayOutputStream()

    fun append(chunk: ByteArray) {
        var start = 0
        for (index in chunk.indices) {
            if (chunk[index] != '\n'.code.toByte()) continue
            append(chunk, start, index)
            flush()
            start = index + 1
        }
        append(chunk, start, chunk.size)
    }

    fun flush() {
        if (pending.size() == 0) return
        val line = pending.toString(Charsets.UTF_8.name()).trimEnd('\r')
        pending.reset()
        if (line.isNotEmpty()) emit(line)
    }

    private fun append(chunk: ByteArray, start: Int, end: Int) {
        if (pending.size() + end - start > MAX_LINE_BYTES) {
            throw IOException("Remote log line exceeds the size limit")
        }
        pending.write(chunk, start, end - start)
    }

    private companion object {
        const val MAX_LINE_BYTES = 256 * 1024
    }
}
