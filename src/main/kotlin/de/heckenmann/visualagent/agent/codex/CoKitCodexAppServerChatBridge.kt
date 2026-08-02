package de.heckenmann.visualagent.agent.codex

import io.github.vupoint.cokit.client.CodexHostPath
import io.github.vupoint.cokit.client.CodexNotification
import io.github.vupoint.cokit.client.CodexRpc
import io.github.vupoint.cokit.client.ItemType
import io.github.vupoint.cokit.client.ModelName
import io.github.vupoint.cokit.client.ThreadDeleteParams
import io.github.vupoint.cokit.client.ThreadId
import io.github.vupoint.cokit.client.ThreadStartParams
import io.github.vupoint.cokit.client.TurnId
import io.github.vupoint.cokit.client.TurnInput
import io.github.vupoint.cokit.client.TurnInterruptParams
import io.github.vupoint.cokit.client.TurnStartParams
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.springframework.ai.chat.prompt.Prompt
import java.nio.file.Path

/** CoKit-backed app-server bridge for one configured Codex CLI executable and model. */
internal class CoKitCodexAppServerChatBridge(
    private val connectionFactory: CodexAppServerConnector,
    private val executable: Path,
    private val workingDirectory: Path,
    private val model: String,
) : CodexAppServerChatBridge {
    override suspend fun complete(prompt: Prompt): CodexAppServerChatResult {
        val chunks = stream(prompt).toList()
        return CodexAppServerChatResult(model, chunks.joinToString(separator = "", transform = CodexAppServerChatChunk::content))
    }

    override fun stream(prompt: Prompt): Flow<CodexAppServerChatChunk> =
        channelFlow {
            connectionFactory.connect(executable, workingDirectory).use { connection ->
                val events = Channel<CodexNotification>(Channel.UNLIMITED)
                val collector =
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        connection.client.notifications.collect(events::send)
                    }
                var threadId: ThreadId? = null
                var turnId: TurnId? = null
                try {
                    val thread =
                        connection.client
                            .request(
                                CodexRpc.Thread.Start,
                                ThreadStartParams(
                                    cwd = CodexHostPath(workingDirectory.toString()),
                                    model = ModelName(model),
                                ),
                            ).thread
                    threadId = thread.id
                    val turn =
                        connection.client
                            .request(
                                CodexRpc.Turn.Start,
                                TurnStartParams(
                                    threadId = thread.id,
                                    input = listOf(TurnInput.Text(prompt.toCodexInput())),
                                    model = ModelName(model),
                                ),
                            ).turn
                    turnId = turn.id
                    consumeTurn(turn.id, events) { chunk -> send(chunk) }
                } catch (cancelled: CancellationException) {
                    interrupt(connection, threadId, turnId)
                    throw cancelled
                } finally {
                    collector.cancel()
                    events.close()
                    deleteThread(connection, threadId)
                }
            }
        }

    private suspend fun consumeTurn(
        turnId: TurnId,
        events: Channel<CodexNotification>,
        emit: suspend (CodexAppServerChatChunk) -> Unit,
    ) {
        var emittedText = false
        withTimeout(OPERATION_TIMEOUT_MILLIS) {
            while (true) {
                when (val event = events.receive()) {
                    is CodexNotification.AgentMessageDelta ->
                        if (event.turnId == turnId) {
                            emittedText = true
                            emit(CodexAppServerChatChunk(model, event.delta, terminal = false))
                        }
                    is CodexNotification.ItemCompleted ->
                        if (
                            event.turnId == turnId &&
                            !emittedText &&
                            event.item.type == ItemType.AgentMessage &&
                            !event.item.text.isNullOrBlank()
                        ) {
                            emittedText = true
                            emit(CodexAppServerChatChunk(model, event.item.text.orEmpty(), terminal = false))
                        }
                    is CodexNotification.TurnCompleted ->
                        if (event.turn.id == turnId) {
                            emit(CodexAppServerChatChunk(model, "", terminal = true))
                            return@withTimeout
                        }
                    is CodexNotification.TurnFailed ->
                        if (event.turn.id == turnId) error(event.turn.error?.message ?: "Codex turn failed")
                    is CodexNotification.Error ->
                        if (event.turnId == turnId && !event.willRetry) error(event.error.message)
                    else -> Unit
                }
            }
        }
    }

    private suspend fun interrupt(
        connection: CodexAppServerConnection,
        threadId: ThreadId?,
        turnId: TurnId?,
    ) {
        if (threadId == null || turnId == null) return
        withContext(NonCancellable + Dispatchers.IO) {
            runCatching {
                withTimeout(INTERRUPT_TIMEOUT_MILLIS) {
                    connection.client.request(CodexRpc.Turn.Interrupt, TurnInterruptParams(threadId, turnId))
                }
            }
        }
    }

    private suspend fun deleteThread(
        connection: CodexAppServerConnection,
        threadId: ThreadId?,
    ) {
        if (threadId == null) return
        withContext(NonCancellable + Dispatchers.IO) {
            runCatching {
                withTimeout(CLEANUP_TIMEOUT_MILLIS) {
                    connection.client.request(CodexRpc.Thread.Delete, ThreadDeleteParams(threadId))
                }
            }
        }
    }

    private fun Prompt.toCodexInput(): String =
        getInstructions().joinToString("\n\n") { message ->
            "${message.messageType.value}:\n${message.text}"
        }

    private companion object {
        private const val OPERATION_TIMEOUT_MILLIS = 300_000L
        private const val INTERRUPT_TIMEOUT_MILLIS = 3_000L
        private const val CLEANUP_TIMEOUT_MILLIS = 3_000L
    }
}
