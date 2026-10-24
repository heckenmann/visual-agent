package de.heckenmann.visualagent.agent

import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.part.MediaPart
import org.springframework.ai.chat.messages.part.MessagePart
import org.springframework.ai.chat.messages.part.TextPart
import org.springframework.ai.tokenizer.TokenCountEstimator
import org.springframework.stereotype.Component
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper

/** Uses Spring AI's sealed message-part serialization instead of inventing provider payload types. */
@Component
class ProviderReplayCodec(
    private val mapper: JsonMapper = JsonMapper.builder().findAndAddModules().build(),
) {
    /** Encodes ordered native parts with an explicit provider/profile/model replay boundary. */
    fun encode(
        profileId: String,
        modelId: String,
        message: AssistantMessage,
    ): ProviderReplayState =
        ProviderReplayState(
            FORMAT,
            profileId,
            modelId,
            serializeParts(message),
        )

    /** Measures all ordered native parts, including opaque reasoning and tool arguments, without logging them. */
    fun serializedPartsSize(message: AssistantMessage): Int = serializeParts(message).length

    private fun serializeParts(message: AssistantMessage): String =
        mapper.writerFor(object : TypeReference<List<MessagePart>>() {}).writeValueAsString(message.parts)

    /** Restores only a matching origin while preserving budgeted text and current presentation metadata. */
    fun restore(
        state: ProviderReplayState,
        profileId: String,
        modelId: String,
        fallback: AssistantMessage,
    ): AssistantMessage? {
        if (state.format != FORMAT || state.providerProfileId != profileId || state.modelId != modelId) return null
        val parts = mapper.readValue(state.partsJson, object : TypeReference<List<MessagePart>>() {})
        val restored =
            AssistantMessage
                .builder()
                .parts(parts)
                .properties(fallback.metadata)
                .build()
        return if (restored.text == fallback.text) restored else restored.withBudgetedText(fallback.text.orEmpty())
    }

    /** Estimates native input items not already counted as visible text, excluding non-replayed generated media. */
    fun additionalTokens(
        state: ProviderReplayState,
        estimator: TokenCountEstimator,
    ): Int {
        val parts =
            mapper
                .readValue(state.partsJson, object : TypeReference<List<MessagePart>>() {})
                .filterNot { it is TextPart || it is MediaPart }
        return if (parts.isEmpty()) {
            0
        } else {
            estimator.estimate(
                mapper.writerFor(object : TypeReference<List<MessagePart>>() {}).writeValueAsString(parts),
            )
        }
    }

    private companion object {
        const val FORMAT = "openai.responses.parts.v1"
    }
}
