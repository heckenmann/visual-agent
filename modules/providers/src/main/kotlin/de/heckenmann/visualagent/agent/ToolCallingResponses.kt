package de.heckenmann.visualagent.agent

import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.metadata.ChatGenerationMetadata
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.model.tool.ToolExecutionResult
import org.springframework.ai.chat.model.ChatResponse as SpringChatResponse

/** Preserves Spring AI return-direct conversion. */
internal fun buildDirectResponse(
    originalResponse: SpringChatResponse,
    toolExecutionResult: ToolExecutionResult,
): ChatResponse {
    val directGenerations = ToolExecutionResult.buildGenerations(toolExecutionResult)
    val directContent = directGenerations.firstOrNull()?.let { it.output.text.orEmpty() }.orEmpty()
    return ChatResponse(
        model = originalResponse.metadata.model,
        message = Message(role = "assistant", content = directContent),
        done = true,
    )
}

/** Combines ordered native streaming chunks for tool execution. */
internal fun aggregateStreamingResponse(chunks: List<SpringChatResponse>): SpringChatResponse? {
    if (chunks.isEmpty()) return null
    val lastChunk = chunks.last()
    val content =
        chunks.joinToString("") {
            it.result
                ?.output
                ?.text
                ?.orEmpty() ?: ""
        }
    val toolCalls =
        chunks.flatMap {
            it.result
                ?.output
                ?.toolCalls
                .orEmpty()
        }
    val assistantMessage =
        AssistantMessage
            .builder()
            .content(content)
            .toolCalls(toolCalls)
            .build()
    val generation =
        Generation(
            assistantMessage,
            ChatGenerationMetadata
                .builder()
                .finishReason(lastChunk.result?.metadata?.finishReason ?: if (lastChunk.result != null) "stop" else null)
                .build(),
        )
    return SpringChatResponse(listOf(generation))
}

/** Converts provider response identity without altering streamed content. */
internal fun SpringChatResponse.toVisualAgentResponse(
    requestId: String? = null,
    round: Int? = null,
    sequence: Int? = null,
    normalizeContent: Boolean = true,
): ChatResponse =
    ProviderTurnResponseMapper.toChatResponse(
        ProviderTurnResponseMapper.fromSpring(
            this,
            requestId = requestId,
            round = round,
            sequence = sequence,
        ),
        normalizeContent = normalizeContent,
    )
