package io.github.logsleuth.app.core.importer

import android.content.Context
import android.net.Uri
import io.github.logsleuth.app.data.db.SessionDao
import io.github.logsleuth.app.data.db.SessionEntity
import java.io.File
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Imports log files shared into the app: plain `.log/.txt` files and zip
 * bundles produced by logsleuth-sdk or by our own session export.
 */
@Singleton
class LogImporter @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val sessionDao: SessionDao,
) {
    data class ImportPreview(
        val name: String,
        val lineCount: Long,
        val deviceInfo: String?,
        val fromSdk: Boolean,
    )

    suspend fun import(uri: Uri): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Cannot read $uri")
            require(bytes.isNotEmpty()) { "Empty file" }

            val isZip = bytes.size >= 4 &&
                bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()

            val dir = File(context.filesDir, "imports").apply { mkdirs() }
            val displayName = queryDisplayName(uri) ?: "import"

            if (isZip) {
                val zip = ZipInputStream(bytes.inputStream()).use { z ->
                    var entry = z.nextEntry
                    var log: ByteArray? = null
                    var meta: String? = null
                    while (entry != null) {
                        when {
                            entry.name.endsWith(".log") || entry.name.endsWith(".txt") -> {
                                if (log == null) log = z.readBytes()
                            }

                            entry.name == "meta.txt" -> meta = z.readBytes().toString(Charsets.UTF_8)
                        }
                        z.closeEntry()
                        entry = z.nextEntry
                    }
                    log to meta
                }
                val logBytes = zip.first ?: error("No log file inside zip")
                val file = File(dir, "import_${System.currentTimeMillis()}.log")
                file.writeBytes(logBytes)
                sessionDao.insert(
                    SessionEntity(
                        name = displayName.substringBeforeLast('.'),
                        filePath = file.absolutePath,
                        startedAt = System.currentTimeMillis(),
                        endedAt = System.currentTimeMillis(),
                        lineCount = logBytes.toString(Charsets.UTF_8).lines().size.toLong(),
                        accessKind = "IMPORT",
                        imported = true,
                    ),
                )
            } else {
                val file = File(dir, "import_${System.currentTimeMillis()}.log")
                file.writeBytes(bytes)
                sessionDao.insert(
                    SessionEntity(
                        name = displayName.substringBeforeLast('.'),
                        filePath = file.absolutePath,
                        startedAt = System.currentTimeMillis(),
                        endedAt = System.currentTimeMillis(),
                        lineCount = bytes.toString(Charsets.UTF_8).lines().size.toLong(),
                        accessKind = "IMPORT",
                        imported = true,
                    ),
                )
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    }.getOrNull()
}
