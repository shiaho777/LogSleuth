package io.github.shiaho777.logsleuth.app.core.remoteadb

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.EOFException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * One TCP connection to a remote adbd: handshake, key authentication and
 * a single `shell:` stream. Blocking IO by design — callers run it on
 * Dispatchers.IO and cancel by closing the socket.
 */
class AdbConnection(
    private val host: String,
    private val port: Int,
    private val keys: AdbKeyManager,
    connectTimeoutMs: Int = 8_000,
) : AutoCloseable {

    private val socket = Socket()
    private lateinit var input: BufferedInputStream
    private lateinit var output: BufferedOutputStream

    var localId = 1
        private set
    var remoteId = 0
        private set

    /** True once the shell service is open and WRTE data may arrive. */
    var shellOpen = false
        private set

    init {
        socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
        socket.tcpNoDelay = true
        // Reads block until data or close; a watchdog on the caller side
        // closes the socket to unblock them.
        socket.soTimeout = 0
        input = BufferedInputStream(socket.inputStream, 64 * 1024)
        output = BufferedOutputStream(socket.outputStream, 64 * 1024)
    }

    fun send(message: ByteArray) {
        output.write(message)
        output.flush()
    }

    fun readMessage(): AdbProtocol.Message {
        val headerBytes = readFully(24)
        val header = AdbProtocol.parseHeader(headerBytes)
            ?: throw AdbException("short header")
        val payload = if (header.length > 0) readFully(header.length) else ByteArray(0)
        if (!AdbProtocol.verifyPayload(header, payload)) {
            throw AdbException("payload checksum mismatch")
        }
        return AdbProtocol.Message(header.command, header.arg0, header.arg1, payload)
    }

    private fun readFully(size: Int): ByteArray {
        val buf = ByteArray(size)
        var off = 0
        while (off < size) {
            val n = input.read(buf, off, size - off)
            if (n < 0) throw EOFException("connection closed")
            off += n
        }
        return buf
    }

    /**
     * CNXN → AUTH dance. adbd challenges with a 20-byte token; we sign it
     * with our key. If the key is not trusted yet, adbd shows the "Allow
     * USB debugging?" dialog on the remote screen; once the user accepts,
     * the same exchange ends in CNXN. Returns when authenticated.
     */
    fun authenticate(maxRounds: Int = 30) {
        send(AdbProtocol.cnxn())
        var sentPublicKey = false
        var rounds = 0
        while (true) {
            val message = readMessage()
            when (message.command) {
                AdbProtocol.CNXN -> return
                AdbProtocol.AUTH -> {
                    if (message.arg0 != AdbProtocol.AUTH_TOKEN) {
                        throw AdbException("unexpected AUTH type ${message.arg0}")
                    }
                    if (++rounds > maxRounds) {
                        throw AdbException("authentication not accepted (is the dialog on the remote device answered?)")
                    }
                    send(AdbProtocol.auth(AdbProtocol.AUTH_SIGNATURE, keys.signToken(message.payload)))
                    if (!sentPublicKey) {
                        // Offer the public key so adbd can remember it after
                        // the user taps "Always allow".
                        val line = keys.publicKeyLine() + "\n"
                        send(AdbProtocol.auth(AdbProtocol.AUTH_RSAPUBLICKEY, line.toByteArray(Charsets.UTF_8)))
                        sentPublicKey = true
                    }
                }
                AdbProtocol.STLS -> throw AdbException("remote requires TLS; plain TCP pairing is not supported")
                else -> throw AdbException("unexpected message 0x${Integer.toHexString(message.command)} during handshake")
            }
        }
    }

    /** Opens a shell service and waits for the OKAY that names both ids. */
    fun openShell(command: String) {
        localId = 1
        send(AdbProtocol.open(localId, "shell:$command"))
        while (true) {
            val message = readMessage()
            when (message.command) {
                AdbProtocol.OKAY -> {
                    remoteId = message.arg0
                    shellOpen = true
                    return
                }
                AdbProtocol.CLSE -> throw AdbException("remote refused the shell service")
                else -> throw AdbException("unexpected message 0x${Integer.toHexString(message.command)} opening shell")
            }
        }
    }

    /**
     * Reads shell output until the stream ends. WRTE payloads are handed to
     * [onData]; each is acknowledged so adbd keeps sending. CLSE ends the
     * loop. Returns the remote exit status when shell_v2 delivered one.
     */
    fun readShell(onData: (ByteArray) -> Unit): Int? {
        var exitStatus: Int? = null
        while (true) {
            val message = try {
                readMessage()
            } catch (e: EOFException) {
                break
            }
            when (message.command) {
                AdbProtocol.WRTE -> {
                    if (message.payload.isNotEmpty()) onData(message.payload)
                    send(AdbProtocol.okay(localId, remoteId))
                }
                AdbProtocol.CLSE -> {
                    // shell_v2 packs the exit code into arg0 of the close.
                    exitStatus = message.arg0
                    send(AdbProtocol.close(localId, remoteId))
                    break
                }
                AdbProtocol.OKAY -> Unit
                else -> Unit
            }
        }
        shellOpen = false
        return exitStatus
    }

    override fun close() {
        runCatching { socket.close() }
    }
}