package de.heckenmann.visualagent.agent

import org.springframework.ai.openai.responses.OpenAiResponsesException
import reactor.core.publisher.Flux
import org.springframework.ai.chat.model.ChatResponse as SpringChatResponse

/** Bounds retained native response content independently of the model's token/context budget. */
internal fun Flux<SpringChatResponse>.withResponsePartLimit(
    codec: ProviderReplayCodec,
    maximumCharacters: Int = 16_777_216,
): Flux<SpringChatResponse> {
    require(maximumCharacters > 0)
    val source = this
    return Flux.defer {
        var retainedCharacters = 0L
        source.doOnNext { response ->
            retainedCharacters += response.results.sumOf { codec.serializedPartsSize(it.output).toLong() }
            if (retainedCharacters > maximumCharacters) {
                throw OpenAiResponsesException("response_size_limit", "The provider response exceeded the local resource limit.")
            }
        }
    }
}
