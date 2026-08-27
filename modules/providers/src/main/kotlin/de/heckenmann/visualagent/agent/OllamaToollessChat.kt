package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.ollama.OllamaPromptFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.reactive.asFlow
import org.springframework.ai.ollama.api.OllamaApi
import org.springframework.ai.ollama.api.OllamaChatOptions
import org.springframework.ai.ollama.api.OllamaApi.ChatResponse as OllamaChatResponse

/**
 * Sends a tool-less Ollama chat request directly through the [OllamaApi] client.
 *
 * Spring AI's [org.springframework.ai.ollama.OllamaChatModel] always sets
 * `tools` to a non-null empty list (`List.of()`) in the serialised JSON body,
 * because the [OllamaApi.ChatRequest] builder defaults to that. Some Ollama
 * endpoints (notably certain cloud variants) reject the empty `tools: []`
 * payload with HTTP 500. By bypassing the chat model and building the
 * [OllamaApi.ChatRequest] ourselves without calling `.tools(...)`, Jackson
 * omits the field entirely and the remote endpoint accepts the request.
 *
 * Additionally, [OllamaChatOptions.toMap] places `model`, `format`,
 * `keep_alive`, and `truncate` into the map. These are top-level fields in the
 * Ollama API and must not appear inside `options`. This class strips them out.
 */
internal object OllamaToollessChat {
    /**
     * Sends a tool-less chat request and returns the raw provider response.
     *
     * @param ollamaApi Ollama API client to use
     * @param promptFactory Factory used to build the prompt
     * @param request Provider-neutral request context
     * @param selectedModel Model name to send to the API
     * @return Chat response from the provider
     */
    fun execute(
        ollamaApi: OllamaApi,
        promptFactory: OllamaPromptFactory,
        request: ChatRequestContext,
        selectedModel: String,
    ): ChatResponse {
        val prompt = promptFactory.buildPrompt(request, selectedModel)
        val messages = prompt.instructions.map { msg -> msg.toOllamaMessage(selectedModel) }
        val chatRequest =
            buildChatRequest(
                selectedModel = selectedModel,
                messages = messages,
                stream = false,
                options = prompt.options as OllamaChatOptions,
            )
        val response = ollamaApi.chat(chatRequest)
        return ProviderTurnResponseMapper.toChatResponse(ProviderTurnResponseMapper.fromOllama(response))
    }

    /**
     * Streams a tool-less chat response as a [Flow] of partial [ChatResponse]s.
     *
     * @param ollamaApi Ollama API client to use
     * @param promptFactory Factory used to build the prompt
     * @param request Provider-neutral request context
     * @param selectedModel Model name to send to the API
     * @return Cold flow of incremental chat responses from the provider
     */
    fun stream(
        ollamaApi: OllamaApi,
        promptFactory: OllamaPromptFactory,
        request: ChatRequestContext,
        selectedModel: String,
    ): Flow<ChatResponse> =
        flow {
            val prompt = promptFactory.buildPrompt(request, selectedModel)
            val messages = prompt.instructions.map { msg -> msg.toOllamaMessage(selectedModel) }
            val chatRequest =
                buildChatRequest(
                    selectedModel = selectedModel,
                    messages = messages,
                    stream = true,
                    options = prompt.options as OllamaChatOptions,
                )
            var sequence = 0
            ollamaApi.streamingChat(chatRequest).asFlow().collect { chunk: OllamaChatResponse ->
                val turn =
                    ProviderTurnResponseMapper
                        .fromOllama(chunk, sequence = sequence++)
                        .let { mapped ->
                            if (mapped.model.isBlank()) mapped.copy(model = selectedModel) else mapped
                        }
                emit(ProviderTurnResponseMapper.toChatResponse(turn))
            }
        }

    /**
     * Builds an [OllamaApi.ChatRequest] with the `tools` field explicitly null.
     *
     * The Spring AI [OllamaApi.ChatRequest.Builder] initialises `tools` with
     * `List.of()` and refuses to accept `null`, so Jackson would otherwise
     * always serialise `"tools": []`. Constructing the record directly via
     * reflection lets us pass `null`, which Jackson omits from the request body.
     *
     * In addition [OllamaChatOptions.toMap] includes `model`, `format`,
     * `keep_alive`, and `truncate` — these are top-level Ollama API fields,
     * not `options` parameters, so they are stripped from the map before it
     * is placed in the `options` field of the request record.
     */
    private fun buildChatRequest(
        selectedModel: String,
        messages: List<OllamaApi.Message>,
        stream: Boolean,
        options: OllamaChatOptions,
    ): OllamaApi.ChatRequest {
        val rawOptions = options.toMap()
        val filteredOptions = rawOptions - TOP_LEVEL_OPTION_KEYS
        val optionsArg: Map<String, Any>? = filteredOptions.takeIf { it.isNotEmpty() }
        val constructor = OllamaApi.ChatRequest::class.java.declaredConstructors[0]
        constructor.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return constructor.newInstance(
            selectedModel,
            messages,
            stream,
            null,
            null,
            null,
            optionsArg,
            null,
        ) as OllamaApi.ChatRequest
    }

    private val TOP_LEVEL_OPTION_KEYS = setOf("model", "format", "keep_alive", "truncate")

    private fun org.springframework.ai.chat.messages.Message.toOllamaMessage(selectedModel: String): OllamaApi.Message {
        val role =
            when (this) {
                is org.springframework.ai.chat.messages.SystemMessage ->
                    if (selectedModel.endsWith(":cloud")) {
                        OllamaApi.Message.Role.USER
                    } else {
                        OllamaApi.Message.Role.SYSTEM
                    }
                is org.springframework.ai.chat.messages.AssistantMessage -> OllamaApi.Message.Role.ASSISTANT
                else -> OllamaApi.Message.Role.USER
            }
        return OllamaApi.Message
            .builder(role)
            .content(text.orEmpty())
            .build()
    }
}
