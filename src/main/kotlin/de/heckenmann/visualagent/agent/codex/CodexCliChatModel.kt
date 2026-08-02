package de.heckenmann.visualagent.agent.codex

import de.heckenmann.visualagent.agent.CancellationToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.reactor.asFlux
import kotlinx.coroutines.runBlocking
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.metadata.ChatGenerationMetadata
import org.springframework.ai.chat.metadata.ChatResponseMetadata
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.tool.ToolCallback
import reactor.core.publisher.Flux

/**
 * Spring AI chat model backed by the authenticated local Codex CLI app-server connection.
 */
internal class CodexCliChatModel(
    private val bridge: CodexAppServerChatBridge,
    private val cancellationToken: CancellationToken? = null,
    private val toolCallbacks: List<ToolCallback> = emptyList(),
) : ChatModel {
    override fun call(prompt: Prompt): ChatResponse =
        runBlocking(Dispatchers.IO) {
            bridge.complete(prompt, cancellationToken, toolCallbacks).toSpringResponse()
        }

    override fun stream(prompt: Prompt): Flux<ChatResponse> =
        bridge
            .stream(prompt, cancellationToken, toolCallbacks)
            .flowOn(Dispatchers.IO)
            .asFlux()
            .map(CodexAppServerChatChunk::toSpringResponse)
}

/**
 * App-server boundary required by the Spring AI adapter.
 *
 * The concrete implementation owns protocol initialization, thread/turn correlation,
 * cancellation, and conversion of JSON-RPC notifications into ordered chunks.
 */
internal interface CodexAppServerChatBridge {
    /**
     * Completes a prompt through one Codex app-server turn.
     *
     * @param prompt Spring AI prompt to map to the app-server thread and turn
     * @return Completed assistant response
     */
    suspend fun complete(
        prompt: Prompt,
        cancellationToken: CancellationToken? = null,
        toolCallbacks: List<ToolCallback> = emptyList(),
    ): CodexAppServerChatResult

    /**
     * Streams one Codex app-server turn as ordered assistant chunks.
     *
     * @param prompt Spring AI prompt to map to the app-server thread and turn
     * @return Ordered assistant chunks ending in a terminal chunk
     */
    fun stream(
        prompt: Prompt,
        cancellationToken: CancellationToken? = null,
        toolCallbacks: List<ToolCallback> = emptyList(),
    ): Flow<CodexAppServerChatChunk>
}

/**
 * Completed assistant response returned by the app-server boundary.
 *
 * @property model Effective Codex model selected for the turn
 * @property content Complete assistant text
 */
internal data class CodexAppServerChatResult(
    val model: String,
    val content: String,
)

/**
 * One assistant chunk returned by the app-server boundary.
 *
 * @property model Effective Codex model selected for the turn
 * @property content Ordered assistant text delta
 * @property terminal Whether this chunk completes the turn
 */
internal data class CodexAppServerChatChunk(
    val model: String,
    val content: String,
    val terminal: Boolean,
)

private fun CodexAppServerChatResult.toSpringResponse(): ChatResponse =
    ChatResponse(
        listOf(Generation(AssistantMessage(content))),
        ChatResponseMetadata.builder().model(model).build(),
    )

private fun CodexAppServerChatChunk.toSpringResponse(): ChatResponse =
    ChatResponse(
        listOf(
            Generation(
                AssistantMessage(content),
                ChatGenerationMetadata.builder().apply { if (terminal) finishReason("STOP") }.build(),
            ),
        ),
        ChatResponseMetadata.builder().model(model).build(),
    )
