package io.github.shiaho777.logsleuth.app.mcp

import android.util.Log
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.net.ssl.SSLException
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

internal data class McpState(
    val starting: Boolean = false,
    val active: Boolean = false,
    val failed: Boolean = false,
    val paired: Boolean = false,
    val activeCalls: Int = 0,
    val lastContact: Long? = null,
)

@javax.inject.Singleton
internal class McpStatus @Inject constructor() {
    private val value = MutableStateFlow(McpState())
    val state = value.asStateFlow()

    fun set(state: McpState) { value.value = state }
    fun contact(delta: Int) = value.update {
        it.copy(activeCalls = (it.activeCalls + delta).coerceAtLeast(0), lastContact = System.currentTimeMillis())
    }
    fun paired(paired: Boolean) = value.update { it.copy(paired = paired) }
}

internal class McpListener @Inject constructor(
    private val identity: McpIdentity,
    private val contract: McpContract,
    private val actions: McpActions,
    private val files: McpFiles,
    private val status: McpStatus,
) {
    private val sockets = ConcurrentHashMap.newKeySet<SSLSocket>()
    private val connections = Semaphore(6)
    private val mutations = Mutex()

    @Volatile private var listener: SSLServerSocket? = null

    fun start(): SSLServerSocket {
        val socket = identity.tls.serverSocketFactory.createServerSocket() as SSLServerSocket
        try {
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), MCP_PORT), 6)
            socket.enabledProtocols = socket.supportedProtocols.filter { it in setOf("TLSv1.2", "TLSv1.3") }.toTypedArray()
            listener = socket
            return socket
        } catch (failure: Exception) {
            socket.close()
            throw failure
        }
    }

    fun serve(scope: CoroutineScope, server: SSLServerSocket) {
        while (scope.isActive && !server.isClosed) {
            val socket = try {
                server.accept() as SSLSocket
            } catch (failure: SocketException) {
                if (server.isClosed) return
                throw failure
            }
            if (!connections.tryAcquire()) {
                socket.close()
                continue
            }
            sockets.add(socket)
            if (server.isClosed) {
                sockets.remove(socket)
                socket.close()
                connections.release()
                return
            }
            scope.launch {
                try {
                    socket.use { serveConnection(it) }
                } catch (_: IOException) {
                } finally {
                    sockets.remove(socket)
                    connections.release()
                }
            }
        }
    }

    fun close() {
        listener?.let { runCatching { it.close() } }
        listener = null
        disconnect()
    }

    fun disconnect() {
        sockets.forEach { runCatching { it.close() } }
    }

    private suspend fun serveConnection(socket: SSLSocket) {
        socket.soTimeout = 5_000
        try {
            socket.startHandshake()
        } catch (failure: SSLException) {
            if (io.github.shiaho777.logsleuth.app.BuildConfig.DEBUG) {
                Log.w("LogSleuthMcp", "Local TLS handshake failed", failure)
            }
            return
        }
        val input = DataInputStream(socket.inputStream)
        val output = DataOutputStream(socket.outputStream)
        while (!socket.isClosed && serveFrame(input, output)) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
        }
    }

    private suspend fun serveFrame(input: DataInputStream, output: DataOutputStream): Boolean {
        val length = input.readInt()
        if (length !in 1..MAX_FRAME) return false
        val frame = ByteArray(length).also(input::readFully)
        val request = try {
            JSONObject(frame.toString(Charsets.UTF_8))
        } catch (_: org.json.JSONException) {
            return false
        }
        val reply = try {
            json("result" to invoke(request))
        } catch (failure: McpFailure) {
            json("error" to json("code" to failure.code, "message" to failure.message))
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: org.json.JSONException) {
            json("error" to json("code" to "INVALID_ARGUMENT", "message" to "The request is invalid."))
        } catch (failure: Exception) {
            Log.w("LogSleuthMcp", "Tool failed: ${failure.javaClass.simpleName}")
            json("error" to json("code" to "OPERATION_FAILED", "message" to "The operation failed. Check the current state before retrying."))
        }
        var bytes = reply.toString().toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_FRAME) {
            bytes = json("error" to json("code" to "RESULT_TOO_LARGE", "message" to "Request a smaller result."))
                .toString().toByteArray(Charsets.UTF_8)
        }
        output.apply {
            writeInt(bytes.size)
            write(bytes)
            flush()
        }
        return true
    }

    private suspend fun invoke(request: JSONObject): JSONObject {
        if (request.optInt("version") != MCP_VERSION) refuse("VERSION_MISMATCH", "Install matching LogSleuth and MCP builds.")
        val tool = request.getString("tool")
        val args = request.getJSONObject("arguments")
        if (tool == "pair") {
            return mutations.withLock {
                val token = identity.pair(args.getString("code"))
                files.clear()
                status.paired(true)
                json("token" to token)
            }
        }
        val token = request.optString("token")
        fun authorize() {
            if (!identity.authorize(token)) refuse("UNPAIRED", "Pair the Termux bridge with LogSleuth.")
        }
        authorize()
        status.contact(1)
        try {
            if (tool.startsWith("transfer_")) return mutations.withLock {
                authorize()
                files.execute(tool, args)
            }
            val readOnly = contract.validate(tool, args)
            return if (readOnly) actions.execute(tool, args) else mutations.withLock {
                authorize()
                actions.execute(tool, args)
            }
        } finally {
            status.contact(-1)
        }
    }

    private companion object {
        const val MAX_FRAME = 65_536
    }
}
