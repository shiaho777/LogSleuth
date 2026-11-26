package io.github.shiaho777.logsleuth.app.mcp

import androidx.test.platform.app.InstrumentationRegistry
import java.net.InetAddress
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class McpTlsTest {
    @Test
    fun androidKeystoreCertificateCanAuthenticateTheLocalMcpServer() = runBlocking(Dispatchers.IO) {
        withTimeout(15_000L) {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val identity = McpIdentity(context)
            val address = InetAddress.getByName("127.0.0.1")
            (identity.tls.serverSocketFactory.createServerSocket(0, 1, address) as SSLServerSocket).use { server ->
                server.soTimeout = 5_000
                val fingerprint = identity.createPairing().split('.')[1]
                val serving = async {
                    (server.accept() as SSLSocket).use { socket ->
                        socket.soTimeout = 5_000
                        socket.startHandshake()
                        socket.outputStream.write(42)
                        socket.outputStream.flush()
                    }
                }
                val trust = object : X509TrustManager {
                    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

                    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
                        throw CertificateException("Client certificates are outside the loopback protocol")
                    }

                    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                        val actual = MessageDigest.getInstance("SHA-256").digest(chain.first().encoded)
                            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
                        if (actual != fingerprint) throw CertificateException("Certificate pin mismatch")
                    }
                }
                val client = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
                (client.socketFactory.createSocket(address, server.localPort) as SSLSocket).use { socket ->
                    socket.soTimeout = 5_000
                    socket.startHandshake()
                    assertEquals(42, socket.inputStream.read())
                }
                serving.await()
            }
        }
    }
}
