package de.heckenmann.visualagent.agent.codex

import de.heckenmann.visualagent.agent.CancellationToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.reactor.asFlux
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.metadata.ChatGenerationMetadata
import org.springframework.ai.chat.metadata.ChatResponseMetadata
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.tool.ToolCallback
import reactor.core.publisher.Flux
import java.nio.file.Path
import org.springframework.ai.chat.model.ChatResponse as SpringChatResponse

/** Spring AI chat model backed by a clean-room Codex app-server client. */
internal class CodexAppServerChatModel(
    private val executable: Path,
    private val model: String,
    private val toolCallbacks: List<ToolCallback>,
    private val workingDirectory: Path,
    private val showReasoningSummary: Boolean = false,
) : ChatModel {
    override fun call(prompt: Prompt): SpringChatResponse = runBlocking { complete(prompt) }

    override fun stream(prompt: Prompt): Flux<SpringChatResponse> = streamFlow(prompt).asFlux()

    /** Executes a complete request while preserving the provider cancellation token. */
    suspend fun complete(
        prompt: Prompt,
        cancellationToken: CancellationToken? = null,
    ): SpringChatResponse {
        val content = StringBuilder()
        var last: SpringChatResponse? = null
        streamFlow(prompt, cancellationToken).collect { response ->
            response.result
                ?.output
                ?.text
                ?.let(content::append)
            last = response
        }
        val terminal = requireNotNull(last) { "Codex returned no response" }
        return response(content.toString(), done = terminal.hasFinishReasons(setOf("stop")))
    }

    /** Streams native assistant deltas as Spring AI responses. */
    fun streamFlow(
        prompt: Prompt,
        cancellationToken: CancellationToken? = null,
    ): Flow<SpringChatResponse> =
        flow {
            cancellationToken?.throwIfCancelled()
            val transport = CodexAppServerTransport(executable, workingDirectory)
            val cancellationRegistration = cancellationToken?.onCancelled(transport::close)
            try {
                transport.start()
                val thread = transport.request("thread/start", threadParams(prompt))
                val threadId = thread.threadId()
                transport.request("turn/start", turnParams(prompt, threadId))
                var pendingDelta: String? = null
                var receivedMultipleDeltas = false
                withTimeout(TURN_TIMEOUT_MILLIS) {
                    while (true) {
                        cancellationToken?.throwIfCancelled()
                        when (val event = transport.receive()) {
                            is CodexRpcMessage.Request ->
                                handleServerRequest(transport, event)
                            is CodexRpcMessage.Notification ->
                                when (event.method) {
                                    "item/agentMessage/delta" -> {
                                        val delta =
                                            event.params["delta"]
                                                ?.jsonPrimitive
                                                ?.contentOrNull
                                                .orEmpty()
                                        if (delta.isNotEmpty()) {
                                            pendingDelta?.let { emit(response(it, done = false)) }
                                            receivedMultipleDeltas = receivedMultipleDeltas || pendingDelta != null
                                            pendingDelta = delta
                                        }
                                    }
                                    "item/reasoning/summaryTextDelta" -> {
                                        if (showReasoningSummary) {
                                            val summary =
                                                event.params["delta"]
                                                    ?.jsonPrimitive
                                                    ?.contentOrNull
                                                    .orEmpty()
                                            if (summary.isNotEmpty()) {
                                                emit(response("<think>$summary</think>", done = false))
                                            }
                                        }
                                    }
                                    "turn/completed" -> {
                                        val turn = event.params["turn"]?.jsonObject
                                        val status = turn?.get("status")?.jsonPrimitive?.contentOrNull ?: "completed"
                                        if (status != "completed") {
                                            val message =
                                                turn
                                                    ?.get("error")
                                                    ?.jsonObject
                                                    ?.get("message")
                                                    ?.jsonPrimitive
                                                    ?.contentOrNull
                                                    ?: "Codex turn $status"
                                            error(message)
                                        }
                                        pendingDelta?.let { lastDelta ->
                                            if (receivedMultipleDeltas) {
                                                emit(response(lastDelta, done = false))
                                            } else {
                                                val chunks = lastDelta.simulatedChunks()
                                                chunks.forEachIndexed { index, chunk ->
                                                    emit(response(chunk, done = false))
                                                    if (index < chunks.lastIndex) delay(SIMULATED_CHUNK_DELAY_MS)
                                                }
                                            }
                                        }
                                        return@withTimeout
                                    }
                                    "error" -> {
                                        val willRetry =
                                            event.params["willRetry"]
                                                ?.jsonPrimitive
                                                ?.contentOrNull
                                                ?.toBoolean() == true
                                        if (!willRetry) {
                                            val message =
                                                event.params["error"]
                                                    ?.jsonObject
                                                    ?.get("message")
                                                    ?.jsonPrimitive
                                                    ?.contentOrNull
                                                    ?: "Codex app-server request failed"
                                            error(message)
                                        }
                                    }
                                }
                            is CodexRpcMessage.Response -> Unit
                        }
                    }
                }
                emit(response("", done = true))
            } finally {
                cancellationRegistration?.close()
                transport.close()
            }
        }.flowOn(Dispatchers.IO)

    private suspend fun handleServerRequest(
        transport: CodexAppServerTransport,
        request: CodexRpcMessage.Request,
    ) {
        if (request.method != "item/tool/call") {
            transport.respondError(request.id, -32601, "Unsupported Codex server request")
            return
        }
        val toolName = request.params["tool"]?.jsonPrimitive?.contentOrNull
        val callback = toolCallbacks.firstOrNull { it.toolDefinition.name() == toolName }
        if (callback == null) {
            transport.respondError(request.id, -32602, "Tool is not enabled for this request")
            return
        }
        val arguments = request.params["arguments"]?.toString() ?: "{}"
        val result = withContext(Dispatchers.IO) { runCatching { callback.call(arguments) } }
        if (result.isFailure) {
            transport.respond(
                request.id,
                buildJsonObject {
                    put("success", JsonPrimitive(false))
                    put(
                        "contentItems",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("type", JsonPrimitive("inputText"))
                                    put("text", JsonPrimitive(result.exceptionOrNull()?.message ?: "Tool execution failed"))
                                },
                            )
                        },
                    )
                },
            )
        } else {
            transport.respond(
                request.id,
                buildJsonObject {
                    put("success", JsonPrimitive(true))
                    put(
                        "contentItems",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("type", JsonPrimitive("inputText"))
                                    put("text", JsonPrimitive(result.getOrThrow()))
                                },
                            )
                        },
                    )
                },
            )
        }
    }

    private fun threadParams(prompt: Prompt): JsonObject =
        buildJsonObject {
            put("model", JsonPrimitive(model))
            put("cwd", JsonPrimitive(workingDirectory.toAbsolutePath().toString()))
            put("sandbox", JsonPrimitive("read-only"))
            put("approvalPolicy", JsonPrimitive("never"))
            put("ephemeral", JsonPrimitive(true))
            prompt.systemText()?.let { put("developerInstructions", JsonPrimitive(it)) }
            put(
                "dynamicTools",
                buildJsonArray {
                    toolCallbacks.forEach { callback ->
                        add(
                            buildJsonObject {
                                put("type", JsonPrimitive("function"))
                                put("name", JsonPrimitive(callback.toolDefinition.name()))
                                put("description", JsonPrimitive(callback.toolDefinition.description()))
                                put("inputSchema", Json.parseToJsonElement(callback.toolDefinition.inputSchema()))
                            },
                        )
                    }
                },
            )
        }

    private fun turnParams(
        prompt: Prompt,
        threadId: String,
    ): JsonObject =
        buildJsonObject {
            put("threadId", JsonPrimitive(threadId))
            put("model", JsonPrimitive(model))
            put(
                "input",
                buildJsonArray {
                    prompt.instructions
                        .filter { it !is SystemMessage }
                        .forEach { message ->
                            add(
                                buildJsonObject {
                                    put("type", JsonPrimitive("text"))
                                    put("text", JsonPrimitive(messageText(message)))
                                },
                            )
                        }
                },
            )
            put("summary", JsonPrimitive(if (showReasoningSummary) "detailed" else "none"))
        }

    private fun response(
        text: String,
        done: Boolean,
    ): SpringChatResponse =
        SpringChatResponse(
            listOf(
                Generation(
                    AssistantMessage(text),
                    ChatGenerationMetadata.builder().apply { if (done) finishReason("stop") }.build(),
                ),
            ),
            ChatResponseMetadata.builder().model(model).build(),
        )

    private fun Prompt.systemText(): String? =
        instructions
            .filterIsInstance<SystemMessage>()
            .joinToString("\n\n") { it.text.orEmpty() }
            .takeIf(String::isNotBlank)

    private fun messageText(message: org.springframework.ai.chat.messages.Message): String =
        when (message) {
            is AssistantMessage -> "[assistant]\n${message.text.orEmpty()}"
            is UserMessage -> message.text.orEmpty()
            else -> message.text.orEmpty()
        }

    private fun JsonObject.threadId(): String =
        this["thread"]
            ?.jsonObject
            ?.get("id")
            ?.jsonPrimitive
            ?.contentOrNull
            ?: error("Codex did not return a thread id")

    private companion object {
        private const val SIMULATED_CHUNK_DELAY_MS = 16L
        private const val TURN_TIMEOUT_MILLIS = 300_000L
    }
}
