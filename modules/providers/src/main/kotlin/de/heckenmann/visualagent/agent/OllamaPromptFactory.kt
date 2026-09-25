package de.heckenmann.visualagent.agent.ollama

import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.ContextPolicyMetadata
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.RequestContextBudgeter
import de.heckenmann.visualagent.agent.ToolDefinition
import de.heckenmann.visualagent.agent.ToolId
import de.heckenmann.visualagent.agent.provider.ProviderToolCallbacks
import de.heckenmann.visualagent.agent.supportsToolCalling
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.model.tool.ToolCallingChatOptions
import org.springframework.ai.ollama.api.OllamaChatOptions
import org.springframework.ai.tool.ToolCallback
import org.springframework.stereotype.Component
import org.springframework.ai.chat.messages.Message as SpringMessage

/**
 * Builds Ollama prompts with Spring AI tool-calling options and provider-safe tool names.
 */
@Component
class OllamaPromptFactory(
    private val toolRegistry: ProviderToolCallbacks,
    private val contextBudgeter: RequestContextBudgeter = RequestContextBudgeter(),
) {
    /** Returns the exact callbacks selected for the built prompt's context budget. */
    fun callbacks(prompt: Prompt): List<ToolCallback> = (prompt.options as? ToolCallingChatOptions)?.toolCallbacks.orEmpty()

    /**
     * Returns provider-safe function names enabled for the request.
     *
     * @param request Provider-neutral request context
     * @param selectedModel Active model name
     * @return Sorted list of function names exposed to Spring AI
     */
    fun allowedFunctionNames(
        request: ChatRequestContext,
        selectedModel: String,
    ): List<String> =
        if (!request.supportsToolCalling()) {
            emptyList()
        } else {
            callbacks(buildPrompt(request, selectedModel)).map { it.toolDefinition.name() }.distinct().sorted()
        }

    /** Returns the function names actually selected for the prompt's context budget. */
    fun allowedFunctionNames(prompt: Prompt): List<String> = callbacks(prompt).map { it.toolDefinition.name() }.distinct().sorted()

    /**
     * Builds a Spring AI prompt including tool options and strict tool-name guidance.
     *
     * @param request Provider-neutral request context
     * @param selectedModel Active model name
     * @return Spring AI prompt ready for `ChatModel`
     */
    fun buildPrompt(
        request: ChatRequestContext,
        selectedModel: String,
    ): Prompt {
        val supportsTools = request.supportsToolCalling()
        val toolContext =
            request.metadata +
                mapOf("model" to selectedModel) +
                (request.cancellationToken?.let { mapOf("cancellationToken" to it) } ?: emptyMap())
        val callbacks =
            if (supportsTools) {
                toolRegistry.functionCallbacks(
                    enabledTools = request.enabledTools,
                    context = toolContext,
                )
            } else {
                emptyList()
            }
        val exactFunctionNames = callbacks.map { it.toolDefinition.name() }.distinct().sorted()
        val definitions = callbacks.map { it.toProviderDefinition() }
        val helpDefinition = definitions.firstOrNull { it.name == TOOL_HELP_FUNCTION }
        val plan =
            if (helpDefinition == null) {
                null
            } else {
                contextBudgeter.fitWithToolFallback(
                    request = request,
                    fallbackMessages = toolNameGuardMessage(listOf(TOOL_HELP_FUNCTION)) + request.messages,
                    fallbackTools = listOf(helpDefinition),
                    fullMessages = toolNameGuardMessage(exactFunctionNames) + request.messages,
                    fullTools = definitions,
                )
            }
        val budgetedRequest =
            plan?.request
                ?: contextBudgeter.fit(
                    request,
                    toolNameGuardMessage(exactFunctionNames) + request.messages,
                    definitions,
                )
        val selectedCallbacks = callbacks.filter { plan == null || it.toolDefinition.name() in plan.toolNames }
        val selectedNames = selectedCallbacks.map { it.toolDefinition.name() }.distinct().sorted()
        val finalMessages =
            if (plan == null) {
                budgetedRequest.messages
            } else {
                toolNameGuardMessage(selectedNames) + budgetedRequest.messages.filterNot { it.id == TOOL_GUARD_MESSAGE_ID }
            }
        val optionsBuilder =
            OllamaChatOptions
                .builder()
                .model(selectedModel)
        if (supportsTools && selectedCallbacks.isNotEmpty()) {
            optionsBuilder
                .toolCallbacks(selectedCallbacks)
                .toolContext(toolContext)
        }
        budgetedRequest.parameters.temperature?.let(optionsBuilder::temperature)
        budgetedRequest.parameters.topP?.let(optionsBuilder::topP)
        budgetedRequest.parameters.maxTokens?.let(optionsBuilder::numPredict)
        budgetedRequest.options["topK"]?.toIntOrNull()?.let(optionsBuilder::topK)
        budgetedRequest.options["seed"]?.toIntOrNull()?.let(optionsBuilder::seed)
        budgetedRequest.options["repeatPenalty"]?.toDoubleOrNull()?.let(optionsBuilder::repeatPenalty)
        val options = optionsBuilder.build()
        return Prompt(toSpringMessages(finalMessages), options)
    }

    private fun toolNameGuardMessage(exactFunctionNames: List<String>): List<Message> =
        if (exactFunctionNames.isEmpty()) {
            emptyList()
        } else {
            listOf(
                Message(
                    role = "system",
                    id = TOOL_GUARD_MESSAGE_ID,
                    content =
                        """
                        Tool calling strict mode:
                        - You may only call tool functions with these exact names: ${exactFunctionNames.joinToString(", ")}.
                        - Do not invent variants, prefixes, or suffixes.
                        - ${toolRegistry.toolRuntimeGuidance()}
                        - Use `async:true` when a tool can finish in the background.
                        - To list the tools available to you, call tool_help with {"action":"list"}.
                        """.trimIndent(),
                ),
            )
        }

    private fun toSpringMessages(messages: List<Message>): List<SpringMessage> =
        messages.map { msg ->
            val metadata =
                msg.contextPolicy?.let { mapOf(ContextPolicyMetadata.KEY to it.name) }.orEmpty()
            when (msg.role) {
                "system" ->
                    SystemMessage
                        .builder()
                        .text(msg.content)
                        .metadata(metadata)
                        .build()
                "assistant" ->
                    AssistantMessage
                        .builder()
                        .content(msg.content)
                        .properties(metadata)
                        .build()
                else ->
                    UserMessage
                        .builder()
                        .text(msg.content)
                        .metadata(metadata)
                        .build()
            }
        }

    private fun org.springframework.ai.tool.ToolCallback.toProviderDefinition(): ToolDefinition =
        ToolDefinition(
            id = ToolId(toolDefinition.name()),
            name = toolDefinition.name(),
            description = toolDefinition.description(),
            inputSchema = toolDefinition.inputSchema(),
        )

    private companion object {
        const val TOOL_HELP_FUNCTION = "tool_help"
        const val TOOL_GUARD_MESSAGE_ID = "__provider_tool_guard__"
    }

    internal fun updateOutputLimit(
        options: ChatOptions,
        limit: Int,
    ): ChatOptions =
        (options as? OllamaChatOptions)?.mutate()?.numPredict(limit)?.build()
            ?: options.mutate().maxTokens(limit).build()
}
