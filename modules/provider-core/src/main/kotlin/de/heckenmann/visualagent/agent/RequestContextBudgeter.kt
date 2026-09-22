package de.heckenmann.visualagent.agent

import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator
import org.springframework.ai.tokenizer.TokenCountEstimator
import org.springframework.ai.tool.ToolCallback

/**
 * Fits request messages to the limits known for the selected model and request.
 *
 * The budget is calculated from the effective context window, the exact enabled tool schemas,
 * and a response capacity derived from the selected model window. It does not use a fixed history
 * or tool allowance. The latest user message is always retained; if the mandatory request itself
 * cannot fit, the provider call is rejected instead of sending a request that the model cannot
 * process.
 */
class RequestContextBudgeter(
    private val tokenEstimator: TokenCountEstimator = JTokkitTokenCountEstimator(),
) {
    /**
     * Fits a request to its effective context window.
     *
     * @param request Request metadata and model limits
     * @param messages Messages including any provider-specific guard messages
     * @param toolDefinitions Exact tool schemas that will be sent with the request
     * @return Request with the largest newest fitting history and an enforced output limit
     * @throws ContextWindowExceededException when the mandatory system/user/tool payload cannot fit
     */
    fun fit(
        request: ChatRequestContext,
        messages: List<Message>,
        toolDefinitions: List<ToolDefinition> = emptyList(),
    ): ChatRequestContext {
        val contextLimit = request.contextWindow.effectiveLimit() ?: return request.copy(messages = messages)
        val toolTokens = estimateTools(toolDefinitions)
        val latestUser = messages.indexOfLast { it.role == "user" }
        val leadingSystemCount = messages.indexOfFirst { it.role != "system" }.let { index -> if (index < 0) messages.size else index }
        val mandatoryIndices = mandatoryIndices(messages, leadingSystemCount, latestUser)
        val mandatory = mandatoryIndices.map(messages::get)
        val mandatoryTokens = estimateMessages(mandatory)
        val availableForMessages = contextLimit - toolTokens
        if (mandatoryTokens >= availableForMessages) {
            throw ContextWindowExceededException(
                "The selected model context window is too small for the system instructions, " +
                    "enabled tools, latest user turn, and a response (required=$mandatoryTokens, available=$availableForMessages tokens).",
            )
        }

        val effectiveOutput = outputLimit(request, contextLimit, toolTokens, mandatoryTokens)
        val messageBudget = contextLimit - toolTokens - effectiveOutput
        val retained = retainNewestTurns(messages, mandatoryIndices, latestUser, messageBudget)
        return request.copy(messages = retained, parameters = request.parameters.copy(maxTokens = effectiveOutput))
    }

    /**
     * Fits a Spring AI prompt before a subsequent provider round, such as a tool-call follow-up.
     *
     * @param request Request limits for the provider round
     * @param prompt Prompt that is about to be sent
     * @param toolCallbacks Exact callbacks attached to the prompt
     * @return Prompt with older optional messages removed when required
     */
    fun fitPrompt(
        request: ChatRequestContext,
        prompt: Prompt,
        toolCallbacks: List<ToolCallback> = emptyList(),
    ): Prompt {
        val messages =
            prompt.instructions.mapIndexed { index, message ->
                Message(
                    role =
                        when (message) {
                            is SystemMessage -> "system"
                            is UserMessage -> "user"
                            is AssistantMessage -> "assistant"
                            else -> "assistant"
                        },
                    content = message.text.orEmpty(),
                    id = index.toString(),
                )
            }
        val fitted = fit(request, messages, toolCallbacks.map { it.toProviderDefinition() })
        val retainedIds = fitted.messages.mapNotNull { it.id?.toIntOrNull() }.toSet()
        return Prompt(prompt.instructions.filterIndexed { index, _ -> index in retainedIds }, prompt.options)
    }

    private fun mandatoryIndices(
        messages: List<Message>,
        leadingSystemCount: Int,
        latestUser: Int,
    ): Set<Int> =
        buildSet {
            addAll(
                (0 until leadingSystemCount).filter { index ->
                    messages[index].contextPolicy != ConversationContextPolicy.SUMMARY_SOURCE
                },
            )
            if (latestUser >= 0) addAll(latestUser..messages.lastIndex)
        }

    private fun outputLimit(
        request: ChatRequestContext,
        contextLimit: Int,
        toolTokens: Int,
        mandatoryTokens: Int,
    ): Int {
        val available = contextLimit - toolTokens - mandatoryTokens
        val configured = request.parameters.maxTokens ?: request.contextWindow.outputLimit
        val desired = configured ?: (contextLimit / DEFAULT_OUTPUT_FRACTION_DIVISOR).coerceAtLeast(1)
        return desired.coerceIn(1, available)
    }

    private fun retainNewestTurns(
        messages: List<Message>,
        mandatoryIndices: Set<Int>,
        latestUser: Int,
        messageBudget: Int,
    ): List<Message> {
        val retained = mandatoryIndices.toMutableSet()
        optionalLeadingReferenceIndices(messages, mandatoryIndices)
            .asReversed()
            .forEach { retainIfFits(messages, retained, listOf(it), messageBudget) }
        for (turn in completedTurnIndices(messages, latestUser).asReversed()) {
            if (!retainIfFits(messages, retained, turn, messageBudget)) break
        }
        return messages.filterIndexed { index, _ -> index in retained }
    }

    private fun optionalLeadingReferenceIndices(
        messages: List<Message>,
        mandatoryIndices: Set<Int>,
    ): List<Int> =
        messages.indices
            .takeWhile { index -> messages[index].role != "user" }
            .filter { index -> messages[index].contextPolicy == ConversationContextPolicy.SUMMARY_SOURCE }
            .filterNot(mandatoryIndices::contains)

    private fun completedTurnIndices(
        messages: List<Message>,
        latestUser: Int,
    ): List<List<Int>> {
        val userIndices = messages.indices.filter { index -> messages[index].role == "user" }
        if (latestUser < 0) return emptyList()
        return userIndices.dropLast(1).mapIndexed { index, start ->
            val endExclusive = userIndices.getOrElse(index + 1) { latestUser }
            (start until endExclusive).toList()
        }
    }

    private fun retainIfFits(
        messages: List<Message>,
        retained: MutableSet<Int>,
        candidate: List<Int>,
        messageBudget: Int,
    ): Boolean {
        val candidateIndices = retained + candidate
        if (estimateMessages(candidateIndices.sorted().map(messages::get)) > messageBudget) return false
        retained += candidate
        return true
    }

    private fun estimateMessages(messages: List<Message>): Int =
        tokenEstimator.estimate(messages.joinToString("\n") { message -> "${message.role}: ${message.content}" })

    private fun estimateTools(tools: List<ToolDefinition>): Int =
        tokenEstimator.estimate(
            tools.joinToString("\n") { tool ->
                "${tool.name}: ${tool.description}\n${tool.inputSchema}"
            },
        )

    private fun ToolCallback.toProviderDefinition(): ToolDefinition =
        ToolDefinition(
            id = ToolId(toolDefinition.name()),
            name = toolDefinition.name(),
            description = toolDefinition.description(),
            inputSchema = toolDefinition.inputSchema(),
        )

    private companion object {
        const val DEFAULT_OUTPUT_FRACTION_DIVISOR = 4
    }
}

/** Signals that the selected model cannot accept the mandatory request payload. */
class ContextWindowExceededException(
    message: String,
) : IllegalStateException(message)
