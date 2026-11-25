package io.github.shiaho777.logsleuth.app.core.remoteadb

import java.io.DataInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AdbConnectionTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `an untrusted host offers its key once and waits for user approval`() {
        val keys = AdbKeyManager(folder.newFolder())
        val token = ByteArray(20) { it.toByte() }
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { listener ->
            val remote = FutureTask {
                listener.accept().use { peer ->
                    val input = DataInputStream(peer.getInputStream())
                    val output = peer.getOutputStream()
                    peer.soTimeout = 5_000
                    assertEquals(AdbProtocol.CNXN, read(input).command)
                    output.write(AdbProtocol.auth(AdbProtocol.AUTH_TOKEN, token))

                    val signature = read(input)
                    assertEquals(AdbProtocol.AUTH_SIGNATURE, signature.arg0)
                    assertArrayEquals(keys.signToken(token), signature.payload)
                    peer.soTimeout = 150
                    assertThrows(SocketTimeoutException::class.java) { input.readByte() }

                    peer.soTimeout = 5_000
                    output.write(AdbProtocol.auth(AdbProtocol.AUTH_TOKEN, token))
                    val publicKey = read(input)
                    assertEquals(AdbProtocol.AUTH_RSAPUBLICKEY, publicKey.arg0)
                    assertArrayEquals(keys.publicKeyLine().toByteArray(Charsets.UTF_8) + byteArrayOf(0), publicKey.payload)
                    output.write(AdbProtocol.cnxn())
                }
            }
            Thread(remote).apply { isDaemon = true }.start()
            AdbConnection("127.0.0.1", listener.localPort, keys, readTimeoutMs = 5_000).use {
                it.authenticate()
            }
            remote.get(5, TimeUnit.SECONDS)
        }
    }

    private fun read(input: DataInputStream): AdbProtocol.Message {
        val headerData = ByteArray(24).also(input::readFully)
        val header = requireNotNull(AdbProtocol.parseHeader(headerData))
        val data = ByteArray(header.length).also(input::readFully)
        return AdbProtocol.Message(header.command, header.arg0, header.arg1, data)
    }
}
