package de.heckenmann.visualagent.agent.codex

import de.heckenmann.visualagent.agent.CancellationToken
import io.github.vupoint.cokit.client.ApprovalPolicy
import io.github.vupoint.cokit.client.CodexHostPath
import io.github.vupoint.cokit.client.CodexJsonPayload
import io.github.vupoint.cokit.client.CodexNotification
import io.github.vupoint.cokit.client.CodexRpc
import io.github.vupoint.cokit.client.DynamicToolCallContentItem
import io.github.vupoint.cokit.client.DynamicToolCallHandler
import io.github.vupoint.cokit.client.DynamicToolCallResponse
import io.github.vupoint.cokit.client.DynamicToolSpec
import io.github.vupoint.cokit.client.ItemType
import io.github.vupoint.cokit.client.ModelName
import io.github.vupoint.cokit.client.SandboxMode
import io.github.vupoint.cokit.client.SandboxPolicy
import io.github.vupoint.cokit.client.ThreadDeleteParams
import io.github.vupoint.cokit.client.ThreadId
import io.github.vupoint.cokit.client.ThreadInjectItemsParams
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.tool.ToolCallback
import java.nio.file.Path

/** CoKit-backed app-server bridge for one configured Codex CLI executable and model. */
internal class CoKitCodexAppServerChatBridge(
    private val connectionFactory: CodexAppServerConnector,
    private val executable: Path,
    private val workingDirectory: Path,
    private val model: String,
) : CodexAppServerChatBridge {
    override suspend fun complete(
        prompt: Prompt,
        cancellationToken: CancellationToken?,
        toolCallbacks: List<ToolCallback>,
    ): CodexAppServerChatResult {
        val chunks = stream(prompt, cancellationToken, toolCallbacks).toList()
        return CodexAppServerChatResult(model, chunks.joinToString(separator = "", transform = CodexAppServerChatChunk::content))
    }

    override fun stream(
        prompt: Prompt,
        cancellationToken: CancellationToken?,
        toolCallbacks: List<ToolCallback>,
    ): Flow<CodexAppServerChatChunk> =
        channelFlow {
            cancellationToken?.throwIfCancelled()
            connectionFactory.connect(executable, workingDirectory).use { connection ->
                val mappedPrompt = prompt.toCodexPrompt()
                registerDynamicTools(connection, toolCallbacks)
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
                                    approvalPolicy = ApprovalPolicy.OnRequest,
                                    sandbox = SandboxMode.WorkspaceWrite,
                                    model = ModelName(model),
                                    developerInstructions = mappedPrompt.developerInstructions,
                                    dynamicTools = toolCallbacks.toDynamicToolSpecs(),
                                ),
                            ).thread
                    threadId = thread.id
                    if (mappedPrompt.history.isNotEmpty()) {
                        connection.client.request(
                            CodexRpc.Thread.InjectItems,
                            ThreadInjectItemsParams(thread.id, mappedPrompt.history),
                        )
                    }
                    val turn =
                        connection.client
                            .request(
                                CodexRpc.Turn.Start,
                                TurnStartParams(
                                    threadId = thread.id,
                                    input = listOf(TurnInput.Text(mappedPrompt.userInput)),
                                    cwd = CodexHostPath(workingDirectory.toString()),
                                    approvalPolicy = ApprovalPolicy.OnRequest,
                                    sandbox = SandboxPolicy.WorkspaceWrite,
                                    model = ModelName(model),
                                ),
                            ).turn
                    turnId = turn.id
                    cancellationToken?.onCancelled { launch { interrupt(connection, threadId, turnId) } }
                    consumeTurn(turn.id, events, cancellationToken) { chunk -> send(chunk) }
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
        cancellationToken: CancellationToken?,
        emit: suspend (CodexAppServerChatChunk) -> Unit,
    ) {
        var emittedText = false
        withTimeout(OPERATION_TIMEOUT_MILLIS) {
            while (true) {
                cancellationToken?.throwIfCancelled()
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

    private fun registerDynamicTools(
        connection: CodexAppServerConnection,
        callbacks: List<ToolCallback>,
    ) {
        if (callbacks.isEmpty()) return
        val callbacksByName = callbacks.associateBy { it.toolDefinition.name() }
        connection.client.registerDynamicToolCallHandler(
            DynamicToolCallHandler { request ->
                val callback = requireNotNull(callbacksByName[request.tool]) { "Unknown Codex dynamic tool ${request.tool}" }
                val output = withContext(Dispatchers.IO) { callback.call(request.arguments.toJsonString()) }
                DynamicToolCallResponse(
                    success = true,
                    contentItems = listOf(DynamicToolCallContentItem(output)),
                )
            },
        )
    }

    private fun List<ToolCallback>.toDynamicToolSpecs(): List<DynamicToolSpec>? =
        takeIf(List<ToolCallback>::isNotEmpty)?.map { callback ->
            val definition = callback.toolDefinition
            DynamicToolSpec(
                name = definition.name(),
                description = definition.description(),
                inputSchema = CodexJsonPayload.parse(definition.inputSchema()),
            )
        }

    private fun Prompt.toCodexPrompt(): MappedCodexPrompt {
        val instructions = getInstructions()
        val developerInstructions =
            instructions
                .filter { it.messageType.value == "system" }
                .joinToString("\n\n") { it.text.orEmpty() }
                .ifBlank { null }
        val conversation = instructions.filterNot { it.messageType.value == "system" }
        val userInputIndex = conversation.indexOfLast { it.messageType.value == "user" }
        require(userInputIndex >= 0) { "Codex prompt requires a user message" }
        require(userInputIndex == conversation.lastIndex) { "Codex prompt must end with a user message" }
        return MappedCodexPrompt(
            developerInstructions = developerInstructions,
            history = conversation.take(userInputIndex).map { it.toHistoryItem() },
            userInput = conversation[userInputIndex].text.orEmpty(),
        )
    }

    private fun org.springframework.ai.chat.messages.Message.toHistoryItem(): CodexJsonPayload {
        val role = messageType.value
        require(role == "user" || role == "assistant") { "Unsupported Codex history role: $role" }
        val contentType = if (role == "assistant") "output_text" else "input_text"
        val item =
            buildJsonObject {
                put("type", "message")
                put("role", role)
                putJsonArray("content") {
                    add(
                        buildJsonObject {
                            put("type", contentType)
                            put("text", text.orEmpty())
                        },
                    )
                }
            }
        return CodexJsonPayload.parse(item.toString())
    }

    private data class MappedCodexPrompt(
        val developerInstructions: String?,
        val history: List<CodexJsonPayload>,
        val userInput: String,
    )

    private companion object {
        private const val OPERATION_TIMEOUT_MILLIS = 300_000L
        private const val INTERRUPT_TIMEOUT_MILLIS = 3_000L
        private const val CLEANUP_TIMEOUT_MILLIS = 3_000L
    }
}
