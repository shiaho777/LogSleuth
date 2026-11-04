package io.github.shiaho777.logsleuth.app.core.update

import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

/** What one [ApkDownloader.run] attempt ended with. */
internal sealed interface FetchOutcome {
    /** [bytes] of the asset are on disk in the part file. */
    data class Complete(val bytes: Long) : FetchOutcome

    /** The caller asked to stop; the bytes so far stay for a later resume. */
    data class Stopped(val bytes: Long) : FetchOutcome

    /** Network, protocol or size verification failure. */
    data class Failed(val bytes: Long, val failure: FetchFailure) : FetchOutcome
}

/**
 * Why a transfer failed, kept structured so the caller can show a localized
 * message instead of the raw exception text.
 */
internal sealed interface FetchFailure {
    /** The server answered with a non-2xx status (or 416 twice). */
    data class Http(val code: Int) : FetchFailure

    /** The part file does not hold the asset's advertised size. */
    data object Incomplete : FetchFailure

    /** The connection stopped delivering data. */
    data object Stalled : FetchFailure

    /** Anything else, with the exception's message when it had one. */
    data class Io(val message: String?) : FetchFailure
}

/**
 * Streams one APK asset into [part], resuming from whatever is already there.
 *
 * Pure JVM — no Android types — so the resume, retry and pause behaviour is
 * unit tested against a local HTTP server. The caller owns the final file and
 * the UI state; this class only moves bytes.
 *
 * Pause and cancel are noticed within [readTimeoutMs]: every blocking read is
 * bounded, and a timeout loops back to [keepGoing] rather than waiting on a
 * stalled connection. [maxStalls] consecutive silent reads give up instead of
 * spinning forever.
 */
internal class ApkDownloader(
    private val url: String,
    private val part: File,
    /** Asset size reported by the release API; 0 when the API did not say. */
    private val expectedSize: Long,
    private val userAgent: String,
    private val connectTimeoutMs: Int = 10_000,
    /** Short on purpose: this is the pause/cancel reaction time. */
    private val readTimeoutMs: Int = 2_000,
    private val bufferSize: Int = 64 * 1024,
    /** Consecutive silent reads before the transfer is abandoned. */
    private val maxStalls: Int = 5,
    /** Progress throttle; 0 reports every chunk (used by tests). */
    private val progressIntervalMs: Long = 300,
    private val onProgress: (done: Long, total: Long, bps: Long) -> Unit = { _, _, _ -> },
    /** False asks the transfer to stop and keep what it has. */
    private val keepGoing: () -> Boolean = { true },
) {

    fun run(): FetchOutcome {
        val existing = bytesOnDisk()
        // Finished but never renamed (process death between the last byte and
        // the rename): nothing to fetch.
        if (expectedSize > 0 && existing == expectedSize) return FetchOutcome.Complete(existing)

        val first = fetch(existing)
        if (first != null) return first
        // 416: the leftover is not a prefix of this asset. One clean retry.
        part.delete()
        return fetch(0L) ?: FetchOutcome.Failed(0L, FetchFailure.Http(HTTP_RANGE_REJECTED))
    }

    /** One connection. Null when the server rejected the Range with 416. */
    private fun fetch(existing: Long): FetchOutcome? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            setRequestProperty("User-Agent", userAgent)
            if (existing > 0) {
                setRequestProperty("Range", "bytes=$existing-")
                // A compressed transfer would not line up with byte offsets.
                setRequestProperty("Accept-Encoding", "identity")
            }
        }
        try {
            val code = conn.responseCode
            if (code == HTTP_RANGE_REJECTED) return null
            if (code !in 200..299) {
                return FetchOutcome.Failed(bytesOnDisk(), FetchFailure.Http(code))
            }
            // 206 = the server honoured Range: append. 200 = it ignored the
            // header and is sending the whole asset: the old bytes are not a
            // prefix of what follows, so start over.
            val resume = existing > 0 && code == HttpURLConnection.HTTP_PARTIAL
            if (!resume) part.delete()

            var done = if (resume) existing else 0L
            val header = conn.getHeaderFieldLong("Content-Length", -1L)
            val total = when {
                header <= 0 -> expectedSize
                resume -> done + header
                else -> header
            }
            var stalls = 0
            var lastAt = System.nanoTime()
            var lastBytes = done
            FileOutputStream(part, resume).use { out ->
                val buf = ByteArray(bufferSize)
                while (true) {
                    if (!keepGoing()) {
                        out.flush()
                        return FetchOutcome.Stopped(done)
                    }
                    val n = try {
                        conn.inputStream.read(buf)
                    } catch (_: SocketTimeoutException) {
                        if (++stalls > maxStalls) {
                            out.flush()
                            return FetchOutcome.Failed(done, FetchFailure.Stalled)
                        }
                        continue
                    }
                    stalls = 0
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    val now = System.nanoTime()
                    if (now - lastAt >= progressIntervalMs * 1_000_000L) {
                        val bps = ((done - lastBytes) * 1e9 / (now - lastAt)).toLong()
                        onProgress(done, total, bps)
                        lastAt = now
                        lastBytes = done
                    }
                }
                out.flush()
            }
            onProgress(done, total, 0)

            // The file, not the counter, decides: a deleted part mid-flight
            // would otherwise look complete.
            val onDisk = bytesOnDisk()
            if (expectedSize > 0 && onDisk != expectedSize) {
                return FetchOutcome.Failed(onDisk, FetchFailure.Incomplete)
            }
            return FetchOutcome.Complete(onDisk)
        } catch (e: Exception) {
            return FetchOutcome.Failed(bytesOnDisk(), FetchFailure.Io(e.message))
        } finally {
            conn.disconnect()
        }
    }

    private fun bytesOnDisk(): Long = part.length().takeIf { part.isFile } ?: 0L

    private companion object {
        const val HTTP_RANGE_REJECTED = 416
    }
}
