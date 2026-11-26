package io.github.shiaho777.logsleuth.app.mcp

import android.content.Context
import android.net.Uri
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.shiaho777.logsleuth.app.core.importer.ImportCore
import io.github.shiaho777.logsleuth.app.core.importer.LogImporter
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

@Singleton
internal class McpFiles @Inject constructor(
    @ApplicationContext context: Context,
    private val importer: LogImporter,
) {
    private data class Transfer(val file: File, val name: String, val size: Long, val upload: Boolean)

    private val directory = File(context.cacheDir, "mcp-transfers")
    private val lock = Mutex()
    private val transfers = mutableMapOf<String, Transfer>()

    suspend fun clear() = lock.withLock {
        transfers.clear()
        directory.listFiles()?.forEach { it.delete() }
        Unit
    }

    suspend fun export(file: File): JSONObject = lock.withLock {
        val handle = allocate()
        val target = File(directory, handle)
        try {
            file.copyTo(target)
            transfers[handle] = Transfer(target, file.name, target.length(), false)
            json("transferId" to handle, "filename" to file.name, "size" to target.length())
        } catch (error: Exception) {
            target.delete()
            throw error
        }
    }

    suspend fun execute(tool: String, args: JSONObject): JSONObject = lock.withLock {
        if (tool == "transfer_begin") {
            val size = args.optLong("size", -1)
            val name = args.optString("filename")
            if (!SAFE_FILE.matches(name) || size !in 1..ImportCore.MAX_IMPORT_BYTES) {
                refuse("INVALID_TRANSFER", "Choose a ZIP, TXT or LOG file up to 256 MiB.")
            }
            val id = allocate()
            val file = File(directory, id)
            check(file.createNewFile())
            transfers[id] = Transfer(file, name, size, true)
            return@withLock json("transferId" to id)
        }

        val id = args.optString("transferId")
        val transfer = transfers[id] ?: refuse("TRANSFER_NOT_FOUND", "Start a new file transfer.")
        val offset = args.optLong("offset", -1)
        when (tool) {
            "transfer_write" -> {
                val data = try {
                    Base64.decode(args.getString("data"), Base64.NO_WRAP)
                } catch (_: IllegalArgumentException) {
                    refuse("INVALID_TRANSFER", "Invalid file chunk.")
                }
                if (!transfer.upload || offset < 0 || offset != transfer.file.length() ||
                    data.size !in 1..CHUNK || offset + data.size > transfer.size
                ) refuse("INVALID_TRANSFER", "Invalid file position or size.")
                transfer.file.appendBytes(data)
                json("offset" to (offset + data.size))
            }
            "transfer_read" -> {
                if (transfer.upload || offset !in 0..transfer.size) {
                    refuse("INVALID_TRANSFER", "Invalid file position.")
                }
                val data = ByteArray(minOf(CHUNK.toLong(), transfer.size - offset).toInt())
                RandomAccessFile(transfer.file, "r").use {
                    it.seek(offset)
                    it.readFully(data)
                }
                json("data" to Base64.encodeToString(data, Base64.NO_WRAP))
            }
            "transfer_commit" -> {
                if (!transfer.upload || transfer.file.length() != transfer.size) {
                    refuse("INVALID_TRANSFER", "The upload is incomplete.")
                }
                try {
                    val session = importer.import(Uri.fromFile(transfer.file), transfer.name).getOrElse {
                        refuse("IMPORT_FAILED", "The report could not be imported. Check the format and size.")
                    }
                    json("sessionId" to session.toString())
                } finally {
                    transfers.remove(id)
                    transfer.file.delete()
                }
            }
            "transfer_close" -> {
                if (!transfer.file.delete() && transfer.file.exists()) error("Transfer could not be removed")
                transfers.remove(id)
                json("closed" to true)
            }
            else -> refuse("UNKNOWN_TOOL", "Unknown transfer command.")
        }
    }

    private fun allocate(): String {
        val now = System.currentTimeMillis()
        transfers.entries.removeAll { (_, transfer) ->
            if (now - transfer.file.lastModified() < 600_000) false
            else transfer.file.delete() || !transfer.file.exists()
        }
        if (transfers.size >= 4) refuse("TRANSFER_LIMIT", "Finish an existing file transfer and retry.")
        if (!directory.isDirectory && !directory.mkdirs()) error("Cannot create transfer directory")
        return UUID.randomUUID().toString()
    }

    private companion object {
        const val CHUNK = 32_768
        val SAFE_FILE = Regex("^[^/\\\\\\p{Cntrl}]{1,150}\\.(?i:zip|txt|log)$")
    }
}
