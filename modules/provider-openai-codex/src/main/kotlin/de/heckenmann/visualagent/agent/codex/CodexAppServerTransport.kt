package de.heckenmann.visualagent.agent.codex

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Owns one short-lived line-delimited JSON-RPC Codex app-server process. */
internal class CodexAppServerTransport(
    private val executable: Path,
    private val workingDirectory: Path,
) : AutoCloseable {
    private val logger = KotlinLogging.logger {}
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writes = Mutex()
    private val sequence = AtomicLong()
    private val responses = ConcurrentHashMap<String, kotlinx.coroutines.CompletableDeferred<JsonObject>>()
    private val incoming = Channel<CodexRpcMessage>(Channel.UNLIMITED)
    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var readerJob: Job? = null

    @Volatile
    private var closing = false

    /** Starts the process and negotiates the public app-server protocol. */
    suspend fun start() {
        check(process == null) { "Codex app-server transport is already started" }
        closing = false
        val child =
            withContext(Dispatchers.IO) {
                ProcessBuilder(executable.toString(), "app-server", "--listen", "stdio://")
                    .directory(workingDirectory.toFile())
                    .redirectErrorStream(false)
                    .apply {
                        environment().remove("OPENAI_API_KEY")
                        environment().remove("OPENAI_CODEX_API_KEY")
                    }.start()
            }
        process = child
        writer = child.outputStream.bufferedWriter(Charsets.UTF_8)
        readerJob = scope.launch { readLoop(child.inputStream.bufferedReader(Charsets.UTF_8)) }
        scope.launch { drainErrors(child.errorStream.bufferedReader(Charsets.UTF_8)) }
        request(
            "initialize",
            kotlinx.serialization.json.buildJsonObject {
                put(
                    "clientInfo",
                    kotlinx.serialization.json.buildJsonObject {
                        put("name", JsonPrimitive("visual-agent"))
                        put("version", JsonPrimitive("0.1.0"))
                    },
                )
                put(
                    "capabilities",
                    kotlinx.serialization.json.buildJsonObject {
                        put("experimentalApi", JsonPrimitive(true))
                    },
                )
            },
        )
        send(jsonRpcNotification("initialized", kotlinx.serialization.json.buildJsonObject {}))
    }

    /** Sends a request and waits for its correlated result. */
    suspend fun request(
        method: String,
        params: JsonObject,
    ): JsonObject {
        val id = JsonPrimitive(sequence.incrementAndGet())
        val deferred = kotlinx.coroutines.CompletableDeferred<JsonObject>()
        responses[id.toString()] = deferred
        try {
            send(jsonRpcRequest(id, method, params))
            return withTimeout(OPERATION_TIMEOUT.toMillis()) { deferred.await() }
        } finally {
            responses.remove(id.toString())
        }
    }

    /** Receives the next server request or notification. */
    suspend fun receive(): CodexRpcMessage = incoming.receive()

    /** Answers a server-initiated request. */
    suspend fun respond(
        id: JsonElement,
        result: JsonObject,
    ) = send(jsonRpcSuccess(id, result))

    /** Answers a server-initiated request with a protocol error. */
    suspend fun respondError(
        id: JsonElement,
        code: Int,
        message: String,
    ) = send(jsonRpcFailure(id, code, message))

    override fun close() {
        closing = true
        readerJob?.cancel()
        scope.cancel()
        incoming.close()
        responses.values.forEach { it.cancel() }
        responses.clear()
        writer?.runCatching { close() }
        process?.let { child ->
            child.destroy()
            if (child.isAlive) child.destroyForcibly()
        }
        process = null
        writer = null
    }

    private suspend fun send(message: JsonObject) {
        val output = requireNotNull(writer) { "Codex app-server transport is not started" }
        writes.withLock {
            withContext(Dispatchers.IO) {
                output.write(json.encodeToString(JsonObject.serializer(), message))
                output.newLine()
                output.flush()
            }
        }
    }

    private suspend fun readLoop(reader: BufferedReader) {
        try {
            while (true) {
                val line = withContext(Dispatchers.IO) { reader.readLine() } ?: break
                if (line.isBlank()) continue
                dispatch(json.parseToJsonElement(line).jsonObject)
            }
            val failure = IllegalStateException("Codex app-server exited before completing the request")
            responses.values.forEach { it.completeExceptionally(failure) }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            responses.values.forEach { it.completeExceptionally(error) }
        } finally {
            incoming.close()
        }
    }

    private suspend fun drainErrors(reader: BufferedReader) {
        try {
            while (withContext(Dispatchers.IO) { reader.readLine() } != null) {
                currentCoroutineContext().ensureActive()
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (closed: IOException) {
            // Destroying the child process closes stderr while the reader may still be
            // blocked in readLine(). This is an expected shutdown event, not a transport
            // failure. Unexpected closures remain visible at debug level for diagnosis.
            if (!closing) {
                logger.debug(closed) { "Codex app-server stderr stream closed unexpectedly" }
            }
        }
    }

    private suspend fun dispatch(message: JsonObject) {
        val method = message["method"]?.jsonPrimitive?.contentOrNull
        val id = message["id"]
        if (method != null) {
            val params = message["params"]?.jsonObject ?: kotlinx.serialization.json.buildJsonObject {}
            incoming.send(
                if (id != null) {
                    CodexRpcMessage.Request(id, method, params)
                } else {
                    CodexRpcMessage.Notification(method, params)
                },
            )
            return
        }
        if (id == null) return
        val response = responses[id.toString()] ?: return
        val error = message["error"]?.jsonObject
        if (error != null) {
            val detail = error["message"]?.jsonPrimitive?.contentOrNull ?: "Codex app-server request failed"
            response.completeExceptionally(IllegalStateException(detail))
        } else {
            response.complete(message["result"]?.jsonObject ?: kotlinx.serialization.json.buildJsonObject {})
        }
    }

    private companion object {
        private val OPERATION_TIMEOUT: Duration = Duration.ofMinutes(5)
    }
}
