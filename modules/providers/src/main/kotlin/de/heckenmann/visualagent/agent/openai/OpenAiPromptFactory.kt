package de.heckenmann.visualagent.agent.openai

import de.heckenmann.visualagent.agent.ChatRequestContext
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
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.ai.tool.ToolCallback
import org.springframework.stereotype.Component
import org.springframework.ai.chat.messages.Message as SpringMessage

/**
 * Builds Spring AI OpenAI prompts with request-scoped tool callbacks.
 */
@Component
class OpenAiPromptFactory(
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
     * @return Sorted function names exposed to Spring AI
     */
    fun allowedFunctionNames(
        request: ChatRequestContext,
        selectedModel: String,
    ): List<String> =
        if (!request.supportsToolCalling()) {
            emptyList()
        } else {
            toolRegistry
                .functionCallbacks(
                    enabledTools = request.enabledTools,
                    context = request.metadata + mapOf("model" to selectedModel, "provider" to "openai"),
                ).map { it.toolDefinition.name() }
                .distinct()
                .sorted()
        }

    /**
     * Builds an OpenAI prompt with native Spring AI tool-calling options.
     *
     * @param request Provider-neutral request context
     * @param selectedModel Active model name
     * @return Spring AI prompt ready for OpenAI ChatModel execution
     */
    fun buildPrompt(
        request: ChatRequestContext,
        selectedModel: String,
    ): Prompt {
        val toolContext =
            request.metadata +
                mapOf("model" to selectedModel, "provider" to "openai") +
                (request.cancellationToken?.let { mapOf("cancellationToken" to it) } ?: emptyMap())
        val callbacks =
            if (request.supportsToolCalling()) {
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
        val optionsBuilder = OpenAiChatOptions.builder().model(selectedModel)
        if (selectedCallbacks.isNotEmpty()) {
            optionsBuilder
                .toolCallbacks(selectedCallbacks)
                .toolContext(toolContext)
        }
        budgetedRequest.parameters.temperature?.let(optionsBuilder::temperature)
        budgetedRequest.parameters.topP?.let(optionsBuilder::topP)
        budgetedRequest.parameters.maxTokens?.let(optionsBuilder::maxCompletionTokens)
        budgetedRequest.options["seed"]?.toIntOrNull()?.let(optionsBuilder::seed)
        budgetedRequest.options["reasoningEffort"]?.let(optionsBuilder::reasoningEffort)
        budgetedRequest.options["verbosity"]?.let(optionsBuilder::verbosity)
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
            when (msg.role) {
                "system" -> SystemMessage(msg.content)
                "assistant" -> AssistantMessage(msg.content)
                else -> UserMessage(msg.content)
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
        (options as? OpenAiChatOptions)?.mutate()?.maxCompletionTokens(limit)?.build()
            ?: options.mutate().maxTokens(limit).build()
}
