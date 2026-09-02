package de.heckenmann.visualagent.agent.conversation

import de.heckenmann.visualagent.agent.ConversationContextPolicy
import de.heckenmann.visualagent.agent.Message
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator
import org.springframework.ai.tokenizer.TokenCountEstimator

/** Builds a bounded provider context from the complete conversation timeline. */
internal class MainAgentContextAssembler(
    private val tokenEstimator: TokenCountEstimator = JTokkitTokenCountEstimator(),
) {
    /**
     * Projects recent user turns into dialogue plus compact execution summaries.
     *
     * @param history Persisted messages in chronological order
     * @param systemPrompt Main-agent system prompt used for budget calculation
     * @param contextLength Configured provider context length in tokens
     * @return Provider-facing history in chronological order
     */
    fun assemble(
        history: List<Message>,
        systemPrompt: String,
        contextLength: Int,
    ): List<Message> {
        val turns = splitIntoTurns(history)
        if (turns.isEmpty()) return emptyList()
        val selected = turns.takeLast(MAX_RECENT_USER_TURNS)
        val projected = selected.map(::projectTurn)
        val budget = historyBudget(systemPrompt, contextLength)
        val retained = retainWithinBudget(projected, budget)
        val omitted = projected.size - retained.size
        if (omitted == 0) return retained.flatten()
        val notice = Message(role = "system", content = "$omitted older conversation turn(s) omitted from provider context.")
        val messages = retained.flatten()
        val lastAssistant = messages.indexOfLast { it.role == "assistant" }
        if (lastAssistant < 0) return listOf(notice) + messages
        return messages.toMutableList().apply { add(lastAssistant, notice) }
    }

    private fun splitIntoTurns(history: List<Message>): List<List<Message>> {
        val turns = mutableListOf<MutableList<Message>>()
        history.forEach { message ->
            if (message.role == "user") turns.add(mutableListOf())
            if (turns.isEmpty()) turns.add(mutableListOf())
            turns.last() += message
        }
        return turns.filter { turn -> turn.any { it.role == "user" } || turn.any { it.content.isNotBlank() } }
    }

    private fun projectTurn(turn: List<Message>): List<Message> {
        val user = turn.firstOrNull { it.role == "user" }
        val assistant = turn.lastOrNull { it.role == "assistant" }
        val summary = summarize(turn.filter { message -> message !== user && message !== assistant })
        return buildList {
            user?.let(::add)
            if (summary.lines.isNotEmpty() || summary.omittedCount > 0) {
                add(
                    Message(
                        role = "system",
                        content =
                            buildString {
                                append("Execution summary:")
                                if (summary.lines.isNotEmpty()) {
                                    append('\n')
                                    append(summary.lines.joinToString("\n") { "- $it" })
                                }
                                if (summary.omittedCount > 0) {
                                    append("\n- Additional execution events omitted: ${summary.omittedCount}.")
                                }
                            },
                        contextPolicy = ConversationContextPolicy.SUMMARY_SOURCE,
                    ),
                )
            }
            assistant?.let(::add)
        }
    }

    private fun summarize(messages: List<Message>): SummaryResult {
        val deduplicated = LinkedHashMap<String, String>()
        messages
            .filter { it.contextPolicy != ConversationContextPolicy.AUDIT_ONLY }
            .forEach { message ->
                val metadata = parseMetadata(message.metadata)
                val type = metadata["type"] ?: message.role
                val identity =
                    metadata["todoId"] ?: metadata["jobId"]
                        ?: metadata["workspacePath"]?.let { path ->
                            "$path:${metadata["operation"] ?: metadata["eventType"] ?: "mutation"}"
                        }
                        ?: metadata["toolId"]?.let { toolId ->
                            "$toolId:${metadata["providerToolCallId"] ?: metadata["sequence"] ?: metadata["requestId"] ?: message.id}"
                        }
                        ?: metadata["requestId"] ?: message.id ?: message.content
                val statusValue = metadata["status"].orEmpty()
                val keySuffix =
                    if (statusValue.equals("error", ignoreCase = true) ||
                        statusValue.equals("failed", ignoreCase = true) ||
                        statusValue.equals("failure", ignoreCase = true)
                    ) {
                        "$identity:${message.id ?: message.content.hashCode()}"
                    } else {
                        identity
                    }
                val key = "$type:$keySuffix"
                val status = statusValue.takeIf(String::isNotBlank)?.let { " [$it]" }.orEmpty()
                val text = "$type$status: ${message.content.replace(Regex("\\s+"), " ").trim().take(MAX_SUMMARY_CHARS)}"
                deduplicated[key] = text
            }
        val values = deduplicated.values.toList()
        return SummaryResult(
            lines = values.takeLast(MAX_SUMMARY_EVENTS),
            omittedCount = (values.size - MAX_SUMMARY_EVENTS).coerceAtLeast(0),
        )
    }

    private fun parseMetadata(metadata: String?): Map<String, String> =
        runCatching {
            Json
                .parseToJsonElement(metadata.orEmpty())
                .jsonObject
                .mapNotNull { (key, value) -> value.jsonPrimitive.contentOrNull?.let { key to it } }
                .toMap()
        }.getOrDefault(emptyMap())

    private fun historyBudget(
        systemPrompt: String,
        contextLength: Int,
    ): Int =
        (
            contextLength.coerceAtLeast(MIN_CONTEXT_TOKENS) -
                tokenEstimator.estimate(systemPrompt) -
                RESERVED_OUTPUT_TOKENS -
                RESERVED_TOOL_TOKENS
        ).coerceAtLeast(MIN_CONTEXT_TOKENS)

    private fun retainWithinBudget(
        turns: List<List<Message>>,
        budget: Int,
    ): List<List<Message>> {
        val retained = ArrayDeque<List<Message>>()
        var used = 0
        turns.asReversed().forEachIndexed { index, turn ->
            val candidate =
                if (index == 0) {
                    fitLatestTurn(turn, budget)
                } else {
                    turn
                }
            val estimate = estimateTurn(candidate)
            if (retained.isEmpty() || used + estimate <= budget) {
                retained.addFirst(candidate)
                used += estimate
            }
        }
        return retained.toList()
    }

    private fun fitLatestTurn(
        turn: List<Message>,
        budget: Int,
    ): List<Message> {
        val user = turn.firstOrNull { it.role == "user" } ?: return turn
        val userEstimate = tokenEstimator.estimate(user.content)
        if (userEstimate >= budget) return listOf(user)
        var remaining = budget - userEstimate
        val retained =
            turn
                .asReversed()
                .asSequence()
                .filter { it !== user }
                .mapNotNull { message ->
                    val estimate = tokenEstimator.estimate(message.content)
                    if (estimate <= remaining) {
                        remaining -= estimate
                        message
                    } else {
                        val truncated = truncateMessage(message, remaining)
                        remaining = 0
                        truncated
                    }
                }.toList()
                .asReversed()
        return listOf(user) + retained
    }

    private fun truncateMessage(
        message: Message,
        tokenBudget: Int,
    ): Message? {
        if (tokenBudget <= 0 || message.content.isBlank()) return null
        if (tokenEstimator.estimate(message.content) <= tokenBudget) return message
        var low = 1
        var high = message.content.length
        var best = ""
        while (low <= high) {
            val middle = (low + high) / 2
            val candidate = message.content.take(middle) + "…"
            if (tokenEstimator.estimate(candidate) <= tokenBudget) {
                best = candidate
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        return best.takeIf(String::isNotBlank)?.let { message.copy(content = it) }
    }

    private fun estimateTurn(turn: List<Message>): Int = tokenEstimator.estimate(turn.map { message -> message.content }.joinToString("\n"))

    private companion object {
        const val MAX_RECENT_USER_TURNS = 10
        const val MAX_SUMMARY_EVENTS = 24
        const val MAX_SUMMARY_CHARS = 500
        const val RESERVED_OUTPUT_TOKENS = 1024
        const val RESERVED_TOOL_TOKENS = 512
        const val MIN_CONTEXT_TOKENS = 256
    }

    private data class SummaryResult(
        val lines: List<String>,
        val omittedCount: Int,
    )
}
