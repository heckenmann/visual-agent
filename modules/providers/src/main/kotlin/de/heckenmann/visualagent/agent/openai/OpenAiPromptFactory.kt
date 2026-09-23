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
import org.springframework.ai.openai.OpenAiChatOptions
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
        val budgetedRequest =
            contextBudgeter.fit(
                request,
                toolNameGuardMessage(exactFunctionNames) + request.messages,
                callbacks.map { callback -> callback.toProviderDefinition() },
            )
        val optionsBuilder = OpenAiChatOptions.builder().model(selectedModel)
        if (callbacks.isNotEmpty()) {
            optionsBuilder
                .toolCallbacks(callbacks)
                .toolContext(toolContext)
        }
        budgetedRequest.parameters.temperature?.let(optionsBuilder::temperature)
        budgetedRequest.parameters.topP?.let(optionsBuilder::topP)
        budgetedRequest.parameters.maxTokens?.let(optionsBuilder::maxCompletionTokens)
        budgetedRequest.options["seed"]?.toIntOrNull()?.let(optionsBuilder::seed)
        budgetedRequest.options["reasoningEffort"]?.let(optionsBuilder::reasoningEffort)
        budgetedRequest.options["verbosity"]?.let(optionsBuilder::verbosity)
        val options = optionsBuilder.build()
        return Prompt(toSpringMessages(budgetedRequest.messages), options)
    }

    private fun toolNameGuardMessage(exactFunctionNames: List<String>): List<Message> =
        if (exactFunctionNames.isEmpty()) {
            emptyList()
        } else {
            listOf(
                Message(
                    role = "system",
                    content =
                        """
                        Tool calling strict mode:
                        - You may only call tool functions with these exact names: ${exactFunctionNames.joinToString(", ")}.
                        - Do not invent variants, prefixes, or suffixes.
                        - ${toolRegistry.toolRuntimeGuidance()}
                        - Use `async:true` when a tool can finish in the background.
                        - If unsure about a tool name, do not call a tool; ask briefly or answer directly.
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

    internal fun updateOutputLimit(
        options: ChatOptions,
        limit: Int,
    ): ChatOptions =
        (options as? OpenAiChatOptions)?.mutate()?.maxCompletionTokens(limit)?.build()
            ?: options.mutate().maxTokens(limit).build()
}
