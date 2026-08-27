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
    override fun call(prompt: Prompt): SpringChatResponse = runBlocking { complete(prompt) }

    override fun stream(prompt: Prompt): Flux<SpringChatResponse> = streamFlow(prompt).asFlux()

    /** Executes a complete request while preserving the provider cancellation token. */
    suspend fun complete(
        prompt: Prompt,
        cancellationToken: CancellationToken? = null,
    ): SpringChatResponse = collectComplete(streamFlow(prompt, cancellationToken))

    /** Executes a complete Codex turn with one inline image input. */
    suspend fun completeVision(
        image: ByteArray,
        prompt: String,
        cancellationToken: CancellationToken? = null,
    ): SpringChatResponse = collectComplete(streamFlowInternal(Prompt(prompt), cancellationToken, image))

    private suspend fun collectComplete(stream: Flow<SpringChatResponse>): SpringChatResponse {
        val content = StringBuilder()
        var last: SpringChatResponse? = null
        var lastItemId: String? = null
        val reasoning = StringBuilder()
        stream.collect { response ->
            response.metadata.get<String>(CODEX_REASONING)?.let(reasoning::append) ?: run {
                response.result
                    ?.output
                    ?.text
                    ?.let(content::append)
            }
            response.metadata.get<String>(CODEX_ITEM_ID)?.let { lastItemId = it }
            last = response
        }
        val terminal = requireNotNull(last) { "Codex returned no response" }
        return response(
            content.toString(),
            done = terminal.hasFinishReasons(setOf("stop")),
            itemId = lastItemId,
            reasoning = reasoning.toString().takeIf(String::isNotBlank),
        )
    }

    /** Streams native assistant deltas as Spring AI responses. */
    fun streamFlow(
        prompt: Prompt,
        cancellationToken: CancellationToken? = null,
    ): Flow<SpringChatResponse> = streamFlowInternal(prompt, cancellationToken, null)

    private fun streamFlowInternal(
        prompt: Prompt,
        cancellationToken: CancellationToken?,
        image: ByteArray?,
    ): Flow<SpringChatResponse> =
        flow {
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
                                                emit(response(previousDelta, done = false, itemId = pendingItemId))
                                                pendingDelta = null
                                                pendingItemId = null
                                                receivedMultipleDeltas = true
                                            }
                                            pendingDelta?.let { emit(response(it, done = false, itemId = pendingItemId)) }
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
                                                emit(
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
                                                emit(response(lastDelta, done = false, itemId = pendingItemId))
                                            } else {
                                                val chunks = lastDelta.simulatedChunks()
                                                chunks.forEachIndexed { index, chunk ->
                                                    emit(response(chunk, done = false, itemId = pendingItemId))
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
