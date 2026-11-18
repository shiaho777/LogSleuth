package io.github.shiaho777.logsleuth.app.core.remoteadb

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The ADB transport protocol (the one `adb` speaks over TCP), reduced to
 * what a log reader needs: connect, authenticate with a stored key, open a
 * `shell:` service and read its output. Pure Kotlin — no Android types, no
 * third-party dependency — so the framing is unit-testable.
 *
 * Message layout: 24-byte little-endian header (command, arg0, arg1,
 * data length, data checksum, magic = command xor 0xFFFFFFFF) + payload.
 */
object AdbProtocol {
    const val CNXN = 0x4E584E43
    const val AUTH = 0x48545541
    const val OPEN = 0x4E45504F
    const val OKAY = 0x59414B4F
    const val CLSE = 0x45534C43
    const val WRTE = 0x45545257
    const val STLS = 0x534C5453

    const val VERSION = 0x01000001
    const val MAX_PAYLOAD = 256 * 1024

    const val AUTH_TOKEN = 1
    const val AUTH_SIGNATURE = 2
    const val AUTH_RSAPUBLICKEY = 3

    /** Features advertised in CNXN; shell_v2 gives exit-status separation. */
    const val FEATURES = "host::features=shell_v2,cmd"

    data class Message(
        val command: Int,
        val arg0: Int,
        val arg1: Int,
        val payload: ByteArray,
    )

    fun checksum(data: ByteArray): Int {
        var sum = 0L
        for (b in data) sum += (b.toInt() and 0xFF)
        return (sum and 0xFFFFFFFFL).toInt()
    }

    fun encode(command: Int, arg0: Int, arg1: Int, payload: ByteArray = ByteArray(0)): ByteArray {
        require(payload.size <= MAX_PAYLOAD) { "payload too large" }
        val buf = ByteBuffer.allocate(24 + payload.size).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(command)
        buf.putInt(arg0)
        buf.putInt(arg1)
        buf.putInt(payload.size)
        buf.putInt(checksum(payload))
        buf.putInt(command xor -0x1)
        buf.put(payload)
        return buf.array()
    }

    fun cnxn(): ByteArray = encode(CNXN, VERSION, MAX_PAYLOAD, FEATURES.toByteArray(Charsets.UTF_8))

    fun auth(type: Int, data: ByteArray): ByteArray = encode(AUTH, type, 0, data)

    fun open(localId: Int, destination: String): ByteArray =
        // adbd expects the service name NUL-terminated, like adb sends it.
        encode(OPEN, localId, 0, destination.toByteArray(Charsets.UTF_8) + byteArrayOf(0))

    fun okay(localId: Int, remoteId: Int): ByteArray = encode(OKAY, localId, remoteId)

    fun write(localId: Int, remoteId: Int, data: ByteArray): ByteArray =
        encode(WRTE, localId, remoteId, data)

    fun close(localId: Int, remoteId: Int): ByteArray = encode(CLSE, localId, remoteId)

    /** Parses one header; returns null when fewer than 24 bytes are available. */
    fun parseHeader(header: ByteArray): Header? {
        if (header.size < 24) return null
        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val command = buf.int
        val arg0 = buf.int
        val arg1 = buf.int
        val length = buf.int
        val checksum = buf.int
        val magic = buf.int
        if (magic != command xor -0x1) throw AdbException("bad message magic")
        if (length < 0 || length > MAX_PAYLOAD) throw AdbException("bad payload length $length")
        return Header(command, arg0, arg1, length, checksum)
    }

    data class Header(
        val command: Int,
        val arg0: Int,
        val arg1: Int,
        val length: Int,
        val checksum: Int,
    )

    /**
     * Modern adbd (and adb itself) no longer computes payload checksums —
     * the field arrives as 0. Accept that; still verify when a peer does
     * send a real checksum.
     */
    fun verifyPayload(header: Header, payload: ByteArray): Boolean =
        header.checksum == 0 || checksum(payload) == header.checksum
}

class AdbException(message: String, cause: Throwable? = null) : java.io.IOException(message, cause)