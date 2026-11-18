package io.github.shiaho777.logsleuth.app.core.remoteadb

import java.io.File
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec

/**
 * The RSA identity this app presents to remote adbd's. One key pair, kept
 * in app-private storage (PKCS#8), generated on first use. The public part
 * is encoded exactly like `adb`'s adbkey.pub so a device that already
 * trusts the user's adb keys can be made to trust this one by appending it
 * to /data/misc/adb/adb_keys.
 */
class AdbKeyManager(private val dir: File) {

    private val keyFile = File(dir, "adbkey.pk8")

    @Volatile
    private var cached: java.security.KeyPair? = null

    fun keyPair(): java.security.KeyPair {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val pair = if (keyFile.isFile) {
                runCatching { load() }.getOrNull() ?: generate()
            } else {
                generate()
            }
            cached = pair
            return pair
        }
    }

    private fun load(): java.security.KeyPair {
        val factory = KeyFactory.getInstance("RSA")
        val private = factory.generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
        val crt = private as RSAPrivateCrtKey
        val public = factory.generatePublic(RSAPublicKeySpec(crt.modulus, crt.publicExponent))
        return java.security.KeyPair(public, private)
    }

    private fun generate(): java.security.KeyPair {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        val pair = generator.generateKeyPair()
        dir.mkdirs()
        keyFile.writeBytes(pair.private.encoded)
        return pair
    }

    /** Signs adbd's 20-byte challenge the way adb does: SHA1 digest info
     *  prefix + token, PKCS#1 v1.5 padded to the modulus size. */
    fun signToken(token: ByteArray): ByteArray {
        val private = keyPair().private as RSAPrivateCrtKey
        val digestInfo = byteArrayOf(
            0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a,
            0x05, 0x00, 0x04, 0x14,
        ) + token
        val k = (private.modulus.bitLength() + 7) / 8
        val em = ByteArray(k)
        em[0] = 0x00
        em[1] = 0x01
        val psLen = k - digestInfo.size - 3
        for (i in 0 until psLen) em[2 + i] = 0xFF.toByte()
        em[2 + psLen] = 0x00
        System.arraycopy(digestInfo, 0, em, 3 + psLen, digestInfo.size)
        val m = BigInteger(1, em)
        val s = m.modPow(private.privateExponent, private.modulus)
        val out = s.toByteArray()
        // Strip the sign byte / left-pad to exactly k bytes.
        val result = ByteArray(k)
        when {
            out.size == k -> return out
            out.size == k + 1 && out[0] == 0.toByte() ->
                System.arraycopy(out, 1, result, 0, k)
            out.size < k -> System.arraycopy(out, 0, result, k - out.size, out.size)
            else -> throw AdbException("unexpected signature size ${out.size}")
        }
        return result
    }

    /** adb-compatible public key line: base64(struct) + " " + comment. */
    fun publicKeyLine(comment: String = "logsleuth@android"): String {
        val public = keyPair().public as RSAPublicKey
        val n0inv = public.modulus.mod(BigInteger.valueOf(2L).pow(32))
            .modInverse(BigInteger.valueOf(2L).pow(32)).negate()
            .mod(BigInteger.valueOf(2L).pow(32))
        val r = BigInteger.valueOf(2L).pow(4096).mod(public.modulus)
        val buf = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(64) // modulus size in 32-bit words
        buf.putInt(n0inv.toInt())
        putLittleEndian(buf, public.modulus, 256)
        putLittleEndian(buf, r, 256)
        buf.putInt(public.publicExponent.toInt())
        val encoded = java.util.Base64.getEncoder().encodeToString(buf.array())
        return "$encoded $comment"
    }

    private fun putLittleEndian(buf: ByteBuffer, value: BigInteger, size: Int) {
        val bytes = value.toByteArray()
        val out = ByteArray(size)
        // toByteArray is big-endian, possibly with a leading sign byte.
        val src = if (bytes.size > size && bytes[0] == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
        for (i in src.indices) {
            if (i < size) out[i] = src[src.size - 1 - i]
        }
        buf.put(out)
    }
}