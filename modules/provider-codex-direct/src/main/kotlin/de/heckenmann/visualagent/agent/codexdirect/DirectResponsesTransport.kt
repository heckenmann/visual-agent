package de.heckenmann.visualagent.agent.codexdirect

import de.heckenmann.visualagent.agent.provider.DirectProviderAuthMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.part.UnknownPart
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.openai.responses.OpenAiResponsesChatOptions
import org.springframework.ai.openai.responses.OpenAiResponsesMetadata
import reactor.core.publisher.Flux

/** Request-scoped transport adapter; registry callbacks remain on the execution loop's original prompt. */
internal class DirectResponsesTransport(
    private val delegate: ChatModel,
    private val mode: DirectProviderAuthMode,
    private val attachment: DirectPreparedAttachment? = null,
) : ChatModel by delegate {
    override fun call(prompt: Prompt): ChatResponse = delegate.call(transportPrompt(prompt))

    override fun stream(prompt: Prompt): Flux<ChatResponse> = delegate.stream(transportPrompt(prompt))

    /** Uses the documented developer input item instead of unsupported flat OAuth tool declarations. */
    internal fun transportPrompt(prompt: Prompt): Prompt {
        val instructions = prompt.instructions.map { directFileTransportMessage(it, attachment) }
        val mapped = if (instructions == prompt.instructions) prompt else Prompt(instructions, prompt.options)
        if (mode != DirectProviderAuthMode.CHATGPT_OAUTH) return mapped
        val options = prompt.options as OpenAiResponsesChatOptions
        val callbacks = options.toolCallbacks.orEmpty()
        if (callbacks.isEmpty()) return mapped
        val declaration =
            buildJsonObject {
                put("type", "additional_tools")
                put("role", "developer")
                put(
                    "tools",
                    buildJsonArray {
                        callbacks.forEach { callback ->
                            val definition = callback.toolDefinition
                            add(
                                buildJsonObject {
                                    put("type", "function")
                                    put("name", definition.name())
                                    put("description", definition.description())
                                    put("parameters", Json.parseToJsonElement(definition.inputSchema()))
                                    put("strict", options.strict == true)
                                },
                            )
                        }
                    },
                )
            }
        // Spring AI's provider-native extension replays this input item verbatim, including its developer role.
        val carrier =
            AssistantMessage
                .builder()
                .parts(listOf(UnknownPart(OpenAiResponsesMetadata.PROVIDER, "additional_tools", declaration.toString(), null, emptyMap())))
                .build()
        return Prompt(listOf(carrier) + instructions, options.mutate().toolCallbacks(emptyList()).build())
    }
}
