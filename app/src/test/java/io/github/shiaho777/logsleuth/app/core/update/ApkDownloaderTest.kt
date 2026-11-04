package io.github.shiaho777.logsleuth.app.core.update

import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ApkDownloaderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val body = ByteArray(192 * 1024) { (it % 251).toByte() }

    /** The platform's HttpServer module is not on the Android unit-test
     *  classpath, so each test runs against [TestServer] below. */
    private val servers = mutableListOf<TestServer>()

    @After
    fun tearDown() {
        servers.forEach { it.close() }
    }

    private fun serve(
        honorRange: Boolean = true,
        truncate: Boolean = false,
        errorStatus: Int? = null,
    ): TestServer = TestServer(body, honorRange, truncate, errorStatus).also { servers += it }

    private fun downloader(
        port: Int,
        part: File,
        expectedSize: Long = body.size.toLong(),
        keepGoing: () -> Boolean = { true },
        onProgress: (Long, Long, Long) -> Unit = { _, _, _ -> },
    ) = ApkDownloader(
        url = "http://127.0.0.1:$port/apk",
        part = part,
        expectedSize = expectedSize,
        userAgent = "test",
        connectTimeoutMs = 5_000,
        readTimeoutMs = 1_000,
        bufferSize = 32 * 1024,
        progressIntervalMs = 0,
        onProgress = onProgress,
        keepGoing = keepGoing,
    )

    @Test
    fun `fresh download writes the whole asset`() {
        val server = serve()
        val part = File(tmp.root, "app.apk.part")

        val outcome = downloader(server.port, part).run()

        assertTrue(outcome is FetchOutcome.Complete)
        assertEquals(body.size.toLong(), (outcome as FetchOutcome.Complete).bytes)
        assertArrayEquals(body, part.readBytes())
    }

    @Test
    fun `stopping keeps the bytes and the next run resumes with a Range request`() {
        val server = serve()
        val part = File(tmp.root, "app.apk.part")
        var chunks = 0

        val first = downloader(
            server.port,
            part,
            keepGoing = { chunks < 1 },
            onProgress = { _, _, _ -> chunks++ },
        ).run()

        assertTrue(first is FetchOutcome.Stopped)
        val stoppedAt = (first as FetchOutcome.Stopped).bytes
        assertEquals(32L * 1024, stoppedAt)
        assertEquals(stoppedAt, part.length())

        val second = downloader(server.port, part).run()

        assertTrue(second is FetchOutcome.Complete)
        assertArrayEquals(body, part.readBytes())
        assertEquals("bytes=$stoppedAt-", server.ranges.last())
    }

    @Test
    fun `a server that ignores Range restarts instead of appending`() {
        val server = serve(honorRange = false)
        val part = File(tmp.root, "app.apk.part").apply {
            writeBytes(ByteArray(400) { 7 })
        }

        val outcome = downloader(server.port, part).run()

        assertTrue(outcome is FetchOutcome.Complete)
        assertArrayEquals(body, part.readBytes())
    }

    @Test
    fun `a stale part file is discarded after a 416`() {
        val server = serve()
        val part = File(tmp.root, "app.apk.part").apply {
            writeBytes(ByteArray(body.size + 32) { 9 })
        }

        val outcome = downloader(server.port, part).run()

        assertTrue(outcome is FetchOutcome.Complete)
        assertArrayEquals(body, part.readBytes())
    }

    @Test
    fun `a short body fails as incomplete`() {
        val server = serve(truncate = true)
        val part = File(tmp.root, "app.apk.part")

        val outcome = downloader(server.port, part).run()

        assertTrue(outcome is FetchOutcome.Failed)
        assertTrue((outcome as FetchOutcome.Failed).failure is FetchFailure.Incomplete)
    }

    @Test
    fun `an HTTP error keeps the status for the message`() {
        val server = serve(errorStatus = 503)
        val part = File(tmp.root, "app.apk.part")

        val outcome = downloader(server.port, part).run()

        assertTrue(outcome is FetchOutcome.Failed)
        val failure = (outcome as FetchOutcome.Failed).failure
        assertTrue(failure is FetchFailure.Http)
        assertEquals(503, (failure as FetchFailure.Http).code)
    }

    @Test
    fun `a complete part file needs no request`() {
        val server = serve()
        val part = File(tmp.root, "app.apk.part").apply { writeBytes(body) }

        val outcome = downloader(server.port, part).run()

        assertTrue(outcome is FetchOutcome.Complete)
        assertEquals(0, server.requests.get())
    }

    /**
     * Minimal HTTP/1.1 server: enough for `HttpURLConnection` to exercise
     * Range, 206/416 and a truncated body against a real socket.
     */
    private class TestServer(
        private val body: ByteArray,
        private val honorRange: Boolean,
        private val truncate: Boolean,
        private val errorStatus: Int?,
    ) : AutoCloseable {

        private val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val port: Int get() = server.localPort
        val ranges = mutableListOf<String?>()
        val requests = AtomicInteger()

        init {
            Thread({ runLoop() }, "test-http").apply {
                isDaemon = true
                start()
            }
        }

        private fun runLoop() {
            while (!server.isClosed) {
                val socket = try {
                    server.accept()
                } catch (_: IOException) {
                    return
                }
                try {
                    socket.use(::handle)
                } catch (_: IOException) {
                    // The client gave up; the test asserts on its own outcome.
                }
            }
        }

        private fun handle(socket: Socket) {
            val input = socket.getInputStream().bufferedReader(Charsets.ISO_8859_1)
            input.readLine() ?: return
            var range: String? = null
            while (true) {
                val line = input.readLine() ?: break
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon > 0 && line.substring(0, colon).trim().equals("Range", true)) {
                    range = line.substring(colon + 1).trim()
                }
            }
            ranges += range
            requests.incrementAndGet()

            val out = socket.getOutputStream()
            if (errorStatus != null) {
                head(out, "$errorStatus Test Error", "Content-Length: 0")
                out.flush()
                return
            }

            val start = range?.removePrefix("bytes=")?.substringBefore('-')?.toIntOrNull() ?: 0
            when {
                range != null && start >= body.size -> {
                    head(
                        out, "416 Range Not Satisfiable",
                        "Content-Range: bytes */${body.size}",
                        "Content-Length: 0",
                    )
                }
                range != null && honorRange -> {
                    val slice = body.copyOfRange(start, body.size)
                    head(
                        out, "206 Partial Content",
                        "Content-Length: ${slice.size}",
                        "Content-Range: bytes $start-${body.size - 1}/${body.size}",
                    )
                    out.write(slice)
                }
                truncate -> {
                    // No length: the client can only end at EOF, like a dropped
                    // connection that delivered half the asset.
                    head(out, "200 OK", "Connection: close")
                    out.write(body, 0, body.size / 2)
                }
                else -> {
                    head(out, "200 OK", "Content-Length: ${body.size}")
                    out.write(body)
                }
            }
            out.flush()
        }

        private fun head(out: OutputStream, status: String, vararg headers: String) {
            out.write("HTTP/1.1 $status\r\n".toByteArray(Charsets.ISO_8859_1))
            headers.forEach { out.write("$it\r\n".toByteArray(Charsets.ISO_8859_1)) }
            out.write("\r\n".toByteArray(Charsets.ISO_8859_1))
        }

        override fun close() {
            runCatching { server.close() }
        }
    }
}
