package io.github.shiaho777.logsleuth.app.mcp

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.os.SystemClock
import android.util.AtomicFile
import java.io.File
import java.net.Socket
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Principal
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedKeyManager
import javax.security.auth.x500.X500Principal
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject

internal const val MCP_PORT = 47137
internal const val MCP_VERSION = 1

internal class McpFailure(val code: String, message: String) : RuntimeException(message)

internal fun refuse(code: String, message: String): Nothing = throw McpFailure(code, message)

internal fun json(vararg values: Pair<String, Any?>): JSONObject =
    JSONObject().apply { values.forEach { (key, value) -> put(key, value ?: JSONObject.NULL) } }

@Singleton
internal class McpIdentity @Inject constructor(@ApplicationContext context: Context) {
    private val random = SecureRandom()
    private val file = AtomicFile(File(context.noBackupFilesDir, "mcp-pairing"))
    private var secretHash: ByteArray? = if (file.baseFile.isFile) file.readFully() else null
    private var pending: Pair<ByteArray, Long>? = null

    val tls: SSLContext by lazy {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias(KEY)) {
            val spec = KeyGenParameterSpec.Builder(KEY, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
                .setCertificateSubject(X500Principal("CN=LogSleuth MCP"))
                .build()
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
                .apply { initialize(spec) }.generateKeyPair()
        }
        val key = store.getKey(KEY, null) as PrivateKey
        val chain = store.getCertificateChain(KEY).map { it as X509Certificate }.toTypedArray()
        fingerprint = digest(chain.first().encoded).hex()
        val manager = object : X509ExtendedKeyManager() {
            override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? =
                if (keyType == "EC") arrayOf(KEY) else null
            override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String? =
                getServerAliases(keyType, issuers)?.first()
            override fun chooseEngineServerAlias(keyType: String?, issuers: Array<out Principal>?, engine: SSLEngine?): String? =
                getServerAliases(keyType, issuers)?.first()
            override fun getPrivateKey(alias: String?): PrivateKey? = key.takeIf { alias == KEY }
            override fun getCertificateChain(alias: String?): Array<X509Certificate>? = chain.takeIf { alias == KEY }
            override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?) = null
            override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?) = null
        }
        SSLContext.getInstance("TLS").apply { init(arrayOf(manager), null, random) }
    }

    private lateinit var fingerprint: String

    @Synchronized
    fun createPairing(): String {
        check(this::fingerprint.isInitialized)
        val code = randomHex(24)
        pending = digest(code) to (SystemClock.elapsedRealtime() + 120_000L)
        return "lsmcp1.$fingerprint.$code"
    }

    @Synchronized
    fun pair(code: String): String {
        val (expected, expiry) = pending ?: refuse("PAIRING_REQUIRED", "Generate a fresh pairing code in LogSleuth.")
        if (SystemClock.elapsedRealtime() >= expiry) {
            pending = null
            refuse("PAIRING_EXPIRED", "Generate a fresh pairing code in LogSleuth.")
        }
        if (code.length != 48 || !MessageDigest.isEqual(expected, digest(code))) {
            refuse("INVALID_PAIRING", "The pairing code does not match.")
        }
        val token = randomHex(32)
        val hash = digest(token)
        val stream = file.startWrite()
        try {
            stream.write(hash)
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
        secretHash = hash
        pending = null
        return token
    }

    @Synchronized
    fun authorize(token: String): Boolean =
        token.length == 64 && secretHash?.let { MessageDigest.isEqual(it, digest(token)) } == true

    @Synchronized
    fun revoke() {
        file.delete()
        secretHash = null
        pending = null
    }

    @Synchronized
    fun cancelPairing() {
        pending = null
    }

    @Synchronized
    fun isPaired(): Boolean = secretHash != null

    private fun randomHex(size: Int): String = ByteArray(size).also(random::nextBytes).hex()

    private companion object {
        const val KEY = "logsleuth-mcp-tls-v1"
    }
}

private fun digest(value: String): ByteArray = digest(value.toByteArray(Charsets.UTF_8))
private fun digest(value: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(value)
private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
