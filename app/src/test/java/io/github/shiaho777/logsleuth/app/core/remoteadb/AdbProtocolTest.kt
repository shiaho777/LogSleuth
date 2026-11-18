package io.github.shiaho777.logsleuth.app.core.remoteadb

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbProtocolTest {

    @Test
    fun `encode then parse round-trips a message`() {
        val payload = "shell:logcat -v threadtime".toByteArray()
        val bytes = AdbProtocol.encode(AdbProtocol.OPEN, 7, 0, payload)
        val header = AdbProtocol.parseHeader(bytes.copyOf(24))!!
        assertEquals(AdbProtocol.OPEN, header.command)
        assertEquals(7, header.arg0)
        assertEquals(0, header.arg1)
        assertEquals(payload.size, header.length)
        val body = bytes.copyOfRange(24, bytes.size)
        assertArrayEquals(payload, body)
        assertTrue(AdbProtocol.verifyPayload(header, body))
    }

    @Test
    fun `checksum sums unsigned bytes`() {
        assertEquals(0, AdbProtocol.checksum(ByteArray(0)))
        assertEquals(255, AdbProtocol.checksum(byteArrayOf(0xFF.toByte())))
        assertEquals(510, AdbProtocol.checksum(byteArrayOf(0xFF.toByte(), 0xFF.toByte())))
        assertEquals(1 + 2 + 3, AdbProtocol.checksum(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `header magic must match the command`() {
        val bytes = AdbProtocol.cnxn()
        // Corrupt the magic word.
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(20, 0)
        var threw = false
        try {
            AdbProtocol.parseHeader(bytes)
        } catch (e: AdbException) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun `oversized payload length is rejected`() {
        val bytes = AdbProtocol.cnxn()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(12, AdbProtocol.MAX_PAYLOAD + 1)
        // Fix the checksum field is irrelevant; length check fires first.
        var threw = false
        try {
            AdbProtocol.parseHeader(bytes)
        } catch (e: AdbException) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun `short header returns null`() {
        assertNull(AdbProtocol.parseHeader(ByteArray(10)))
    }

    @Test
    fun `corrupted payload fails verification`() {
        val payload = "hello".toByteArray()
        val bytes = AdbProtocol.encode(AdbProtocol.WRTE, 1, 2, payload)
        val header = AdbProtocol.parseHeader(bytes.copyOf(24))!!
        val body = bytes.copyOfRange(24, bytes.size)
        body[0] = (body[0] + 1).toByte()
        assertFalse(AdbProtocol.verifyPayload(header, body))
    }

    @Test
    fun `zero checksum from modern adbd is accepted`() {
        val payload = "hello".toByteArray()
        val bytes = AdbProtocol.encode(AdbProtocol.WRTE, 1, 2, payload)
        // Modern adbd sends checksum 0 regardless of content.
        val buf = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        buf.putInt(16, 0)
        val header = AdbProtocol.parseHeader(bytes.copyOf(24))!!
        assertTrue(AdbProtocol.verifyPayload(header, payload))
    }

    @Test
    fun `cnxn advertises version and max payload`() {
        val bytes = AdbProtocol.cnxn()
        val header = AdbProtocol.parseHeader(bytes.copyOf(24))!!
        assertEquals(AdbProtocol.CNXN, header.command)
        assertEquals(AdbProtocol.VERSION, header.arg0)
        assertEquals(AdbProtocol.MAX_PAYLOAD, header.arg1)
        val features = String(bytes.copyOfRange(24, bytes.size))
        assertTrue(features.contains("shell_v2"))
    }

    @Test
    fun `open carries a null-terminated destination`() {
        val bytes = AdbProtocol.open(3, "shell:echo hi")
        val body = bytes.copyOfRange(24, bytes.size)
        assertEquals(0, body.last().toInt())
        assertEquals("shell:echo hi", String(body, 0, body.size - 1))
    }
}