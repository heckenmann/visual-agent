package de.heckenmann.visualagent.agent.codex

import de.heckenmann.visualagent.agent.CancellationToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.reactor.flux
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.metadata.ChatGenerationMetadata
import org.springframework.ai.chat.metadata.ChatResponseMetadata
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.model.ToolContext
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
    /** Spring AI's imperative compatibility method; the provider uses [completeReactive] reactively. */
    override fun call(prompt: Prompt): SpringChatResponse =
        requireNotNull(completeReactive(prompt).block()) { "Codex returned no response" }

    override fun stream(prompt: Prompt): Flux<SpringChatResponse> = streamReactive(prompt)

    /** Executes a complete request while preserving the provider cancellation token. */
    fun completeReactive(
        prompt: Prompt,
        cancellationToken: CancellationToken? = null,
    ): reactor.core.publisher.Mono<SpringChatResponse> = collectComplete(streamReactive(prompt, cancellationToken))

    /** Executes a complete Codex turn with one inline image input. */
    fun completeVisionReactive(
        image: ByteArray,
        prompt: String,
        cancellationToken: CancellationToken? = null,
    ): reactor.core.publisher.Mono<SpringChatResponse> = collectComplete(streamReactiveInternal(Prompt(prompt), cancellationToken, image))

    /** Streams native assistant deltas as a Reactor stream. */
    fun streamReactive(
        prompt: Prompt,
        cancellationToken: CancellationToken? = null,
    ): Flux<SpringChatResponse> = streamReactiveInternal(prompt, cancellationToken, null)

    private fun collectComplete(stream: Flux<SpringChatResponse>): reactor.core.publisher.Mono<SpringChatResponse> =
        stream.collectList().map { responses ->
            val content = StringBuilder()
            var lastItemId: String? = null
            val reasoning = StringBuilder()
            responses.forEach { response ->
                response.metadata.get<String>(CODEX_REASONING)?.let(reasoning::append) ?: run {
                    response.result
                        ?.output
                        ?.text
                        ?.let(content::append)
                }
                response.metadata.get<String>(CODEX_ITEM_ID)?.let { lastItemId = it }
            }
            val terminal = responses.lastOrNull() ?: error("Codex returned no response")
            response(
                content.toString(),
                done = terminal.hasFinishReasons(setOf("stop")),
                itemId = lastItemId,
                reasoning = reasoning.toString().takeIf(String::isNotBlank),
            )
        }

    private fun streamReactiveInternal(
        prompt: Prompt,
        cancellationToken: CancellationToken?,
        image: ByteArray?,
    ): Flux<SpringChatResponse> =
        flux {
            withContext(Dispatchers.IO) {
                cancellationToken?.throwIfCancelled()
                val transport = CodexAppServerTransport(executable, workingDirectory)
                val cancellationRegistration = cancellationToken?.onCancelled(transport::close)
                try {
                    transport.start()
                    val thread =
                        transport.request(
                            "thread/start",
                            CodexAppServerRequestParams.thread(prompt, model, workingDirectory, toolCallbacks),
                        )
                    val threadId = thread.codexThreadId()
                    transport.request(
                        "turn/start",
                        CodexAppServerRequestParams.turn(prompt, threadId, model, showReasoningSummary, image),
                    )
                    var pendingDelta: String? = null
                    var pendingItemId: String? = null
                    var receivedMultipleDeltas = false
                    var toolCallSequence = 0
                    withTimeout(TURN_TIMEOUT_MILLIS) {
                        while (true) {
                            cancellationToken?.throwIfCancelled()
                            when (val event = transport.receive()) {
                                is CodexRpcMessage.Request ->
                                    if (handleServerRequest(transport, event, toolCallSequence)) toolCallSequence++
                                is CodexRpcMessage.Notification ->
                                    when (event.method) {
                                        "item/agentMessage/delta" -> {
                                            val itemId = event.params["itemId"]?.jsonPrimitive?.contentOrNull
                                            val delta =
                                                event.params["delta"]
                                                    ?.jsonPrimitive
                                                    ?.contentOrNull
                                                    .orEmpty()
                                            if (delta.isNotEmpty()) {
                                                val previousDelta = pendingDelta
                                                if (previousDelta != null && pendingItemId != itemId) {
                                                    send(response(previousDelta, done = false, itemId = pendingItemId))
                                                    pendingDelta = null
                                                    pendingItemId = null
                                                    receivedMultipleDeltas = true
                                                }
                                                pendingDelta?.let { send(response(it, done = false, itemId = pendingItemId)) }
                                                receivedMultipleDeltas = receivedMultipleDeltas || pendingDelta != null
                                                pendingDelta = delta
                                                pendingItemId = itemId
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
                                                    send(
                                                        response(
                                                            text = "",
                                                            done = false,
                                                            itemId = event.params["itemId"]?.jsonPrimitive?.contentOrNull,
                                                            reasoning = summary,
                                                        ),
                                                    )
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
                                                    send(response(lastDelta, done = false, itemId = pendingItemId))
                                                } else {
                                                    val chunks = lastDelta.simulatedChunks()
                                                    chunks.forEachIndexed { index, chunk ->
                                                        send(response(chunk, done = false, itemId = pendingItemId))
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
                    send(response("", done = true))
                } finally {
                    cancellationRegistration?.close()
                    transport.close()
                }
            }
        }

    private suspend fun handleServerRequest(
        transport: CodexAppServerTransport,
        request: CodexRpcMessage.Request,
        sequence: Int,
    ): Boolean {
        if (request.method != "item/tool/call") {
            transport.respondError(request.id, -32601, "Unsupported Codex server request")
            return false
        }
        val toolName = request.params["tool"]?.jsonPrimitive?.contentOrNull
        val callback = toolCallbacks.firstOrNull { it.toolDefinition.name() == toolName }
        if (callback == null) {
            transport.respondError(request.id, -32602, "Tool is not enabled for this request")
            return false
        }
        val arguments = request.params["arguments"]?.toString() ?: "{}"
        val providerCallId = request.params["callId"]?.jsonPrimitive?.contentOrNull ?: request.id
        val toolContext =
            ToolContext(
                mapOf(
                    "providerToolCallId" to providerCallId,
                    "toolCallRound" to 0,
                    "toolCallSequence" to sequence,
                ),
            )
        val result = withContext(Dispatchers.IO) { runCatching { callback.call(arguments, toolContext) } }
        val allowInlineImage = isWorkspaceImageRequest(toolName, arguments)
        val response =
            if (result.isFailure) {
                CodexDynamicToolResultMapper.response(
                    result.exceptionOrNull()?.message ?: "Tool execution failed",
                    allowInlineImage = false,
                    successOverride = false,
                )
            } else {
                CodexDynamicToolResultMapper.response(
                    result.getOrThrow(),
                    allowInlineImage = allowInlineImage,
                )
            }
        transport.respond(request.id, response)
        return true
    }

    private fun isWorkspaceImageRequest(
        toolName: String?,
        arguments: String,
    ): Boolean =
        toolName == WORKSPACE_FILE_TOOL_NAME &&
            runCatching {
                Json
                    .parseToJsonElement(arguments)
                    .jsonObject["action"]
                    ?.jsonPrimitive
                    ?.contentOrNull == WORKSPACE_IMAGE_ACTION
            }.getOrDefault(false)

    private fun response(
        text: String,
        done: Boolean,
        itemId: String? = null,
        reasoning: String? = null,
    ): SpringChatResponse =
        SpringChatResponse(
            listOf(
                Generation(
                    AssistantMessage(text),
                    ChatGenerationMetadata.builder().apply { if (done) finishReason("stop") }.build(),
                ),
            ),
            ChatResponseMetadata
                .builder()
                .model(model)
                .apply {
                    itemId?.let { keyValue(CODEX_ITEM_ID, it) }
                    reasoning?.let { keyValue(CODEX_REASONING, it) }
                }.build(),
        )

    private companion object {
        private const val CODEX_ITEM_ID = "codexItemId"
        private const val CODEX_REASONING = "codexReasoning"
        private const val WORKSPACE_FILE_TOOL_NAME = "workspace_file"
        private const val WORKSPACE_IMAGE_ACTION = "imageBytes"
        private const val SIMULATED_CHUNK_DELAY_MS = 16L
        private const val TURN_TIMEOUT_MILLIS = 300_000L
    }
}
