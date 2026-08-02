package de.heckenmann.visualagent.agent.codex

import io.github.vupoint.cokit.protocol.CodexProtocolJson
import io.github.vupoint.cokit.protocol.JsonRpcMessage
import io.github.vupoint.cokit.rpc.JsonRpcTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/** CoKit JSON-RPC transport over a Visual Agent-owned sanitized Codex process. */
internal class CodexCliCoKitTransport(
    private val childProcess: CodexCliChildProcess,
    scope: CoroutineScope,
) : JsonRpcTransport {
    private val reader = BufferedReader(InputStreamReader(childProcess.stdout, Charsets.UTF_8))
    private val writer = BufferedWriter(OutputStreamWriter(childProcess.stdin, Charsets.UTF_8))
    private val writeMutex = Mutex()
    private val mutableIncoming = MutableSharedFlow<JsonRpcMessage>(extraBufferCapacity = 64)
    private var closed = false
    private val readerJob: Job =
        scope.launch {
            try {
                while (true) {
                    val line = reader.readLine() ?: break
                    require(line.length <= MAX_JSON_LINE_LENGTH) { "Codex JSON-RPC line exceeds the limit" }
                    if (line.isNotBlank()) mutableIncoming.emit(CodexProtocolJson.decodeFromString<JsonRpcMessage>(line))
                }
            } catch (_: java.io.IOException) {
                if (!closed) close()
            }
        }

    override val incoming: SharedFlow<JsonRpcMessage> = mutableIncoming

    override suspend fun send(message: JsonRpcMessage) {
        check(!closed) { "Codex app-server transport is closed" }
        val line = CodexProtocolJson.encodeToString(message)
        require(line.length <= MAX_JSON_LINE_LENGTH) { "Codex JSON-RPC message exceeds the limit" }
        writeMutex.withLock {
            writer.write(line)
            writer.newLine()
            writer.flush()
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        readerJob.cancel()
        runCatching { writer.close() }
        runCatching { reader.close() }
        runCatching { childProcess.stderr.close() }
        childProcess.close()
    }

    private companion object {
        private const val MAX_JSON_LINE_LENGTH = 1_048_576
    }
}
