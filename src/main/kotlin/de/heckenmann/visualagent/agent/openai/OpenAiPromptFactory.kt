package de.heckenmann.visualagent.agent.openai

import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.tools.ToolRegistry
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.stereotype.Component
import org.springframework.ai.chat.messages.Message as SpringMessage

/**
 * Builds Spring AI OpenAI prompts with request-scoped tool callbacks.
 */
@Component
class OpenAiPromptFactory(
    private val toolRegistry: ToolRegistry,
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
        toolRegistry
            .functionCallbacks(
                enabledTools = request.enabledTools,
                context = request.metadata + mapOf("model" to selectedModel, "provider" to "openai"),
            ).map { it.toolDefinition.name() }
            .distinct()
            .sorted()

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
        val callbacks =
            toolRegistry.functionCallbacks(
                enabledTools = request.enabledTools,
                context = request.metadata + mapOf("model" to selectedModel, "provider" to "openai"),
            )
        val exactFunctionNames = callbacks.map { it.toolDefinition.name() }.distinct().sorted()
        val options =
            OpenAiChatOptions
                .builder()
                .model(selectedModel)
                .toolCallbacks(callbacks)
                .toolContext(request.metadata + mapOf("model" to selectedModel, "provider" to "openai"))
                .build()
        return Prompt(toSpringMessages(toolNameGuardMessage(exactFunctionNames) + request.messages), options)
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
                        - Every tool accepts optional runtime fields: `timeoutSeconds` (1..600) and `async` (true/false).
                        - Use `timeoutSeconds` for long operations; use `async:true` when the tool can finish in background.
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
}
