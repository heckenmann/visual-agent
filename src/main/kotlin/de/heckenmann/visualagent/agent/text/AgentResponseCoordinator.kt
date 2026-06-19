package de.heckenmann.visualagent.agent.text

import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.tools.ToolCallEvent
import java.util.concurrent.ConcurrentHashMap

/**
 * Coordinates assistant response normalization, repetition-guard retries, and tool-only followup finalization.
 */
internal class AgentResponseCoordinator(
    private val llmProvider: LLMProvider,
    private val mainSessionId: String,
    private val repetitionGuardRetryLimit: Int,
    private val finishedToolEventsByRequestId: ConcurrentHashMap<String, MutableList<ToolCallEvent>>,
    private val buildMainRequest: (history: List<Message>, requestId: String?) -> ChatRequestContext,
    private val buildMainSystemContextPrompt: () -> String,
    private val loadRecentHistoryFromDb: () -> List<Message>,
) {
    /**
     * Ensures assistant outputs persisted in history are never blank.
     *
     * @param content Raw assistant output
     * @return Trimmed output or a standard placeholder for tool-only/empty turns
     */
    fun normalizeAssistantContent(content: String): String {
        val trimmed = content.trim()
        if (trimmed.isBlank() || isToolCallsOnlyPayload(trimmed)) {
            return "(No text response. See tool results above.)"
        }
        if (ResponseRepetitionGuard.isRunawayRepetition(trimmed)) {
            return "I generated a malformed repeated output. Please repeat your last request and I will answer it cleanly."
        }
        return trimmed
    }

    /**
     * Generates one assistant response and retries once when repetition is detected.
     *
     * @param requestId Request id used to correlate tool events
     * @return Final normalized assistant text
     */
    suspend fun generateAssistantContentWithRepetitionGuard(requestId: String): String {
        var raw =
            llmProvider
                .chat(buildMainRequest(loadRecentHistoryFromDb(), requestId))
                .message
                .content
                .trim()
        if (raw.isBlank()) {
            val finalFromTools = completeToolOnlyTurnWithFollowup(requestId)
            if (!finalFromTools.isNullOrBlank()) return normalizeAssistantContent(finalFromTools)
        }
        if (!ResponseRepetitionGuard.isRunawayRepetition(raw)) {
            return normalizeAssistantContent(raw)
        }
        raw = runRepetitionGuardRetry()
        return normalizeAssistantContent(raw)
    }

    /**
     * Performs one retry after a runaway-repetition response and normalizes the result.
     *
     * @return Normalized assistant output
     */
    suspend fun retryAfterRepetition(): String = normalizeAssistantContent(runRepetitionGuardRetry())

    /**
     * Finalizes a tool-only turn into one concrete assistant message.
     *
     * @param requestId Request id used by the original model call
     * @return Final assistant text or null when no tool events were captured
     */
    suspend fun completeToolOnlyTurnWithFollowup(requestId: String): String? {
        val toolEvents = finishedToolEventsByRequestId.remove(requestId).orEmpty()
        if (toolEvents.isEmpty()) return null
        val summary =
            toolEvents.joinToString("\n") { event ->
                val status = if (event.result.success) "ok" else "error"
                val payload = event.result.content.ifBlank { event.result.error.orEmpty() }
                "- ${event.toolId} [$status]: $payload"
            }
        val followup =
            ChatRequestContext(
                messages =
                    buildList {
                        add(Message("system", buildMainSystemContextPrompt()))
                        add(
                            Message(
                                "system",
                                "Generate the final user-facing answer from the tool results below. Be concrete and do not ask for more context.",
                            ),
                        )
                        addAll(loadRecentHistoryFromDb())
                        add(Message("assistant", "Tool results:\n$summary"))
                    },
                enabledTools = emptySet(),
                metadata = mapOf("sessionId" to mainSessionId, "agent" to "main", "requestId" to "$requestId:finalize"),
            )
        return llmProvider
            .chat(followup)
            .message.content
            .trim()
    }

    /**
     * Detects placeholder tool-call payloads without user-facing content.
     *
     * @param text Raw assistant text
     * @return `true` when payload only contains empty `tool_calls`
     */
    private fun isToolCallsOnlyPayload(text: String): Boolean {
        val compact = text.replace(Regex("\\s+"), "")
        return compact == """{"tool_calls":[]}""" ||
            compact == """```json{"tool_calls":[]}```""" ||
            compact == """```{"tool_calls":[]}```"""
    }

    /**
     * Performs one retry request with an anti-repetition system instruction.
     *
     * @return Raw assistant content from the retry call
     */
    private suspend fun runRepetitionGuardRetry(): String {
        var retryRaw = ""
        repeat(repetitionGuardRetryLimit) {
            val base = buildMainRequest(loadRecentHistoryFromDb(), null)
            val retryInstruction =
                Message(
                    role = "system",
                    content =
                        """
                        Your previous response was malformed due to repeated phrase loops.
                        Regenerate the answer once from scratch.
                        Do not quote or repeat the same sentence pattern.
                        Keep the answer direct and short.
                        """.trimIndent(),
                )
            val retryRequest = base.copy(messages = listOf(retryInstruction) + base.messages)
            retryRaw =
                llmProvider
                    .chat(retryRequest)
                    .message.content
                    .trim()
        }
        return retryRaw
    }
}
