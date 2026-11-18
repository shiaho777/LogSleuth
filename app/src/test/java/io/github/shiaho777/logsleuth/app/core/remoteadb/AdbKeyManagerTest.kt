package io.github.shiaho777.logsleuth.app.core.remoteadb

import java.io.File
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.interfaces.RSAPublicKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AdbKeyManagerTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun manager(dir: File = folder.newFolder()) = AdbKeyManager(dir)

    @Test
    fun `signature verifies against the public key`() {
        val keys = manager()
        val token = ByteArray(20) { (it * 7 + 3).toByte() }
        val signature = keys.signToken(token)
        val public = keys.keyPair().public as RSAPublicKey
        val k = (public.modulus.bitLength() + 7) / 8
        assertEquals(k, signature.size)
        // adbd verifies by RSA "encrypting" the signature with e and
        // checking the PKCS#1 v1.5 SHA-1 DigestInfo structure.
        val recovered = BigInteger(1, signature).modPow(public.publicExponent, public.modulus)
        val em = recovered.toByteArray()
        val padded = if (em.size < k) ByteArray(k - em.size) + em else em.copyOfRange(em.size - k, em.size)
        assertEquals(0x00, padded[0].toInt())
        assertEquals(0x01, padded[1].toInt())
        var i = 2
        while (padded[i] == 0xFF.toByte()) i++
        assertEquals(0x00, padded[i].toInt())
        val digestInfo = padded.copyOfRange(i + 1, padded.size)
        val expectedPrefix = byteArrayOf(
            0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a,
            0x05, 0x00, 0x04, 0x14,
        )
        assertTrue(digestInfo.size == 35)
        assertTrue(digestInfo.copyOfRange(0, 15).contentEquals(expectedPrefix))
        assertTrue(digestInfo.copyOfRange(15, 35).contentEquals(token))
    }

    @Test
    fun `key pair persists across instances`() {
        val dir = folder.newFolder()
        val first = manager(dir).keyPair()
        val second = manager(dir).keyPair()
        assertEquals(first.private, second.private)
        assertEquals(first.public, second.public)
    }

    @Test
    fun `public key line decodes to the adb struct layout`() {
        val keys = manager()
        val line = keys.publicKeyLine("test@unit")
        assertTrue(line.endsWith(" test@unit"))
        val decoded = java.util.Base64.getDecoder().decode(line.substringBefore(' '))
        assertEquals(524, decoded.size)
        val buf = ByteBuffer.wrap(decoded).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(64, buf.int) // 2048 bits = 64 words
        val public = keys.keyPair().public as RSAPublicKey
        // Read the little-endian modulus back and compare.
        val nBytes = ByteArray(256)
        buf.position(8)
        buf.get(nBytes)
        val n = BigInteger(1, nBytes.reversedArray())
        assertEquals(public.modulus, n)
    }
}