package de.heckenmann.visualagent.agent

import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator
import org.springframework.ai.tokenizer.TokenCountEstimator
import org.springframework.ai.tool.ToolCallback
import org.springframework.ai.chat.messages.Message as SpringMessage

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
    /** Fits a request while keeping history ahead of optional provider tool schemas. */
    fun fitWithToolFallback(
        request: ChatRequestContext,
        fallbackMessages: List<Message>,
        fallbackTools: List<ToolDefinition>,
        fullMessages: List<Message>,
        fullTools: List<ToolDefinition>,
        toolHelpName: String = "tool_help",
    ): ContextBudgetPlan {
        val fallback = fitInternal(request, fallbackMessages, fallbackTools)
        val full =
            runCatching { fitInternal(request, fullMessages, fullTools) }
                .getOrNull()
                ?.takeIf { priorityMessages(it.messages) == priorityMessages(fallback.messages) }
        val selected = full ?: fallback
        val status =
            ContextBudgetStatus(
                historyReduced = hasReducedHistory(fullMessages, selected.messages),
                toolSchemasReduced = full == null && fullTools.any { it.name != toolHelpName },
            )
        request.onContextBudgeted?.invoke(status)
        return ContextBudgetPlan(
            request = selected,
            toolNames = if (full != null) fullTools.mapTo(linkedSetOf()) { it.name } else setOf(toolHelpName),
            status = status,
        )
    }

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
        val fitted = fitInternal(request, messages, toolDefinitions)
        request.onContextBudgeted?.invoke(
            ContextBudgetStatus(
                historyReduced = hasReducedHistory(messages, fitted.messages),
                toolSchemasReduced = false,
            ),
        )
        return fitted
    }

    private fun fitInternal(
        request: ChatRequestContext,
        messages: List<Message>,
        toolDefinitions: List<ToolDefinition>,
    ): ChatRequestContext {
        val contextLimit = request.contextWindow.effectiveLimit()
        if (contextLimit == null) {
            val latestUser = messages.indexOfLast { it.role == "user" }
            val leadingSystemCount = messages.indexOfFirst { it.role != "system" }.let { if (it < 0) messages.size else it }
            val retained =
                retainPrioritizedHistory(messages, mandatoryIndices(messages, leadingSystemCount, latestUser), latestUser, Int.MAX_VALUE)
            return request.copy(messages = retained)
        }
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
        val retained = retainPrioritizedHistory(messages, mandatoryIndices, latestUser, messageBudget)
        return request.copy(messages = retained, parameters = request.parameters.copy(maxTokens = effectiveOutput))
    }

    /**
     * Fits a Spring AI prompt before a subsequent provider round, such as a tool-call follow-up.
     *
     * @param request Request limits for the provider round
     * @param prompt Prompt that is about to be sent
     * @param toolCallbacks Exact callbacks attached to the prompt
     * @param outputLimitUpdater Provider-specific option update for the computed response limit
     * @return Prompt with older optional messages removed when required
     */
    fun fitPrompt(
        request: ChatRequestContext,
        prompt: Prompt,
        toolCallbacks: List<ToolCallback> = emptyList(),
        outputLimitUpdater: (ChatOptions, Int) -> ChatOptions = { options, limit ->
            options.mutate().maxTokens(limit).build()
        },
    ): Prompt {
        val firstUserIndex = prompt.instructions.indexOfFirst { it is UserMessage }
        val messages =
            prompt.instructions.mapIndexed { index, message ->
                val preservedPolicy =
                    message.metadata[ContextPolicyMetadata.KEY]
                        ?.toString()
                        ?.let { value -> runCatching { ConversationContextPolicy.valueOf(value) }.getOrNull() }
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
                    contextPolicy =
                        preservedPolicy ?: if (index < firstUserIndex && message is AssistantMessage) {
                            ConversationContextPolicy.SUMMARY_SOURCE
                        } else {
                            null
                        },
                )
            }
        val fitted = fit(request, messages, toolCallbacks.map { it.toProviderDefinition() })
        val retainedIds = fitted.messages.mapNotNull { it.id?.toIntOrNull() }.toSet()
        val fittedById = fitted.messages.mapNotNull { message -> message.id?.toIntOrNull()?.let { it to message } }.toMap()
        val options =
            prompt.options?.let { currentOptions ->
                fitted.parameters.maxTokens?.let { limit -> outputLimitUpdater(currentOptions, limit) } ?: currentOptions
            }
        val instructions =
            prompt.instructions.mapIndexedNotNull { index, instruction ->
                if (index !in retainedIds) {
                    null
                } else {
                    val fittedContent = fittedById[index]?.content ?: return@mapIndexedNotNull instruction
                    if (instruction.text.orEmpty() == fittedContent) instruction else instruction.withText(fittedContent)
                }
            }
        return Prompt(instructions, options)
    }

    private fun SpringMessage.withText(text: String): SpringMessage =
        when (this) {
            is SystemMessage -> mutate().text(text).build()
            is AssistantMessage -> mutate().content(text).build()
            is UserMessage -> mutate().text(text).build()
            else -> this
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

    private fun retainPrioritizedHistory(
        messages: List<Message>,
        mandatoryIndices: Set<Int>,
        latestUser: Int,
        messageBudget: Int,
    ): List<Message> {
        val retained = mandatoryIndices.toMutableSet()
        val replacements = mutableMapOf<Int, Message>()
        var retainedTokens = estimateMessages(mandatoryIndices.map(messages::get))
        val historyEndExclusive = if (latestUser < 0) messages.size else latestUser
        val historyIndices = messages.indices.filter { it < historyEndExclusive && it !in mandatoryIndices }
        val answerIndices =
            historyIndices
                .filter { index ->
                    val message = messages[index]
                    message.role == "assistant" &&
                        message.contextPolicy != ConversationContextPolicy.SUMMARY_SOURCE &&
                        !message.assistantToolTurn
                }.takeLast(MAX_RETAINED_ASSISTANT_TURNS)
                .asReversed()

        for (index in answerIndices) {
            val initiatingUser = (index - 1 downTo 0).firstOrNull { messages[it].role == "user" }
            val pair = listOfNotNull(initiatingUser, index).distinct()
            val addedTokens = retainGroupIfFits(messages, retained, replacements, pair, retainedTokens, messageBudget)
            if (addedTokens != null) {
                retainedTokens += addedTokens
                continue
            }
            val pairContext = retained + pair.filterNot { it == index }
            val pairContextTokens = estimateMessages(pairContext.sorted().map { replacements[it] ?: messages[it] })
            val truncated =
                truncateToRemainingBudget(
                    messages[index],
                    pairContextTokens,
                    messageBudget,
                )
            if (truncated != null) {
                retained += pair
                replacements[index] = truncated
                retainedTokens = pairContextTokens + additionalMessageTokens(listOf(truncated), pairContext.isNotEmpty())
            }
            break
        }

        val toolContextIndices =
            historyIndices
                .filter { index ->
                    val message = messages[index]
                    message.role == "tool" ||
                        message.contextPolicy == ConversationContextPolicy.SUMMARY_SOURCE ||
                        message.assistantToolTurn
                }.asReversed()
        val toolContextIndexSet = toolContextIndices.toHashSet()
        val answerIndexSet = answerIndices.toHashSet()
        toolContextIndices.forEach { index ->
            retainIfFits(messages, retained, replacements, index, retainedTokens, messageBudget)?.let { retainedTokens += it }
        }

        val userIndices = historyIndices.filter { messages[it].role == "user" }
        val turns =
            userIndices
                .mapIndexed { turnIndex, userIndex ->
                    val end = userIndices.getOrNull(turnIndex + 1) ?: historyEndExclusive
                    (userIndex until end).filter { it !in answerIndexSet && it !in toolContextIndexSet }
                }.asReversed()
        turns.forEach { turn ->
            retainGroupIfFits(
                messages,
                retained,
                replacements,
                turn.filterNot(retained::contains),
                retainedTokens,
                messageBudget,
            )?.let { retainedTokens += it }
        }
        val firstUserIndex = userIndices.firstOrNull() ?: historyEndExclusive
        val unpairedHistory = historyIndices.filter { it < firstUserIndex && it !in answerIndexSet && it !in toolContextIndexSet }
        unpairedHistory.asReversed().forEach { index ->
            retainIfFits(messages, retained, replacements, index, retainedTokens, messageBudget)?.let { retainedTokens += it }
        }
        return messages.mapIndexedNotNull { index, message ->
            if (index !in retained) null else replacements[index] ?: message
        }
    }

    private fun retainIfFits(
        messages: List<Message>,
        retained: MutableSet<Int>,
        replacements: Map<Int, Message>,
        candidate: Int,
        retainedTokens: Int,
        messageBudget: Int,
    ): Int? = retainGroupIfFits(messages, retained, replacements, listOf(candidate), retainedTokens, messageBudget)

    private fun retainGroupIfFits(
        messages: List<Message>,
        retained: MutableSet<Int>,
        replacements: Map<Int, Message>,
        candidates: List<Int>,
        retainedTokens: Int,
        messageBudget: Int,
    ): Int? {
        val newIndices = candidates.filterNot(retained::contains)
        if (newIndices.isEmpty()) return 0
        val addedTokens = additionalMessageTokens(newIndices.map { index -> replacements[index] ?: messages[index] }, retained.isNotEmpty())
        if (retainedTokens + addedTokens > messageBudget) return null
        retained += newIndices
        return addedTokens
    }

    private fun truncateToRemainingBudget(
        message: Message,
        retainedTokens: Int,
        messageBudget: Int,
    ): Message? {
        if (message.content.isBlank()) return null
        var low = 1
        var high = message.content.length
        var best: Message? = null
        while (low <= high) {
            val middle = (low + high) / 2
            val candidate =
                message.copy(
                    content =
                        message.content.take(middle) + "\n[Earlier answer truncated to fit the model context window.]",
                )
            if (retainedTokens + additionalMessageTokens(listOf(candidate), true) <= messageBudget) {
                best = candidate
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        return best
    }

    private fun estimateMessages(messages: List<Message>): Int =
        messages.sumOf { message -> tokenEstimator.estimate("${message.role}: ${message.content}") } +
            (messages.size - 1).coerceAtLeast(0)

    private fun additionalMessageTokens(
        messages: List<Message>,
        hasExistingMessages: Boolean,
    ): Int =
        messages.sumOf { message -> tokenEstimator.estimate("${message.role}: ${message.content}") } +
            if (hasExistingMessages) messages.size else (messages.size - 1).coerceAtLeast(0)

    private fun estimateTools(tools: List<ToolDefinition>): Int =
        tokenEstimator.estimate(
            tools.joinToString("\n") { tool ->
                "${tool.name}: ${tool.description}\n${tool.inputSchema}"
            },
        )

    private fun withoutGuard(messages: List<Message>): List<Message> = messages.filterNot { it.id == TOOL_GUARD_MESSAGE_ID }

    private fun priorityMessages(messages: List<Message>): List<Message> {
        val conversation = withoutGuard(messages)
        val latestUser = conversation.indexOfLast { it.role == "user" }
        val leadingSystemCount = conversation.indexOfFirst { it.role != "system" }.let { if (it < 0) conversation.size else it }
        val mandatory = mandatoryIndices(conversation, leadingSystemCount, latestUser)
        val answers =
            conversation.indices
                .filter { index ->
                    val message = conversation[index]
                    index < latestUser &&
                        message.role == "assistant" &&
                        message.contextPolicy != ConversationContextPolicy.SUMMARY_SOURCE &&
                        !message.assistantToolTurn
                }.takeLast(MAX_RETAINED_ASSISTANT_TURNS)
        val initiatingUsers = answers.mapNotNull { answer -> (answer - 1 downTo 0).firstOrNull { conversation[it].role == "user" } }
        return conversation.filterIndexed { index, _ -> index in mandatory || index in answers || index in initiatingUsers }
    }

    private fun hasReducedHistory(
        source: List<Message>,
        retained: List<Message>,
    ): Boolean {
        val sourceConversation =
            withoutGuard(source).filter {
                it.role != "system" ||
                    it.contextPolicy == ConversationContextPolicy.SUMMARY_SOURCE
            }
        val retainedConversation =
            withoutGuard(retained).filter {
                it.role != "system" ||
                    it.contextPolicy == ConversationContextPolicy.SUMMARY_SOURCE
            }
        return sourceConversation != retainedConversation
    }

    private fun ToolCallback.toProviderDefinition(): ToolDefinition =
        ToolDefinition(
            id = ToolId(toolDefinition.name()),
            name = toolDefinition.name(),
            description = toolDefinition.description(),
            inputSchema = toolDefinition.inputSchema(),
        )

    private companion object {
        const val DEFAULT_OUTPUT_FRACTION_DIVISOR = 4
        const val MAX_RETAINED_ASSISTANT_TURNS = 10
        const val TOOL_GUARD_MESSAGE_ID = "__provider_tool_guard__"
    }
}

/** Result of fitting history before optional tools for one provider request. */
data class ContextBudgetPlan(
    val request: ChatRequestContext,
    val toolNames: Set<String>,
    val status: ContextBudgetStatus,
)

/** Signals that the selected model cannot accept the mandatory request payload. */
class ContextWindowExceededException(
    message: String,
) : IllegalStateException(message)
