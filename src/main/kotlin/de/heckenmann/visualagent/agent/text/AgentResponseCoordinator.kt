package de.heckenmann.visualagent.agent.text

import de.heckenmann.visualagent.agent.CancellationToken
import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.ConversationOpsProvider
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.Message
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

/**
 * Coordinates assistant response normalization, repetition-guard retries, and tool-only followup finalization.
 */
@Service
class AgentResponseCoordinator
    @Autowired
    constructor(
        private val llmProvider: LLMProvider,
        private val conversationOps: ConversationOpsProvider,
    ) {
        private val mainSessionId = "main"
        private val repetitionGuardRetryLimit = 1

        /**
         * Normalizes assistant response content: trims whitespace, replaces blank or tool-calls-only
         * payloads with a placeholder, and detects runaway repetition.
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
         * Generates assistant content with repetition-guard retry logic. If the initial response
         * is blank, attempts a tool-only followup; if it contains runaway repetition, retries once.
         */
        suspend fun generateAssistantContentWithRepetitionGuard(
            requestId: String,
            token: CancellationToken? = null,
        ): String {
            token?.throwIfCancelled()
            var raw =
                llmProvider
                    .chat(conversationOps.buildMainRequest(conversationOps.loadRecentHistoryFromDb(), requestId, token))
                    .message
                    .content
                    .trim()
            token?.throwIfCancelled()
            if (raw.isBlank()) {
                val finalFromTools = completeToolOnlyTurnWithFollowup(requestId, token)
                if (!finalFromTools.isNullOrBlank()) return normalizeAssistantContent(finalFromTools)
            }
            if (!ResponseRepetitionGuard.isRunawayRepetition(raw)) {
                return normalizeAssistantContent(raw)
            }
            raw = runRepetitionGuardRetry(token)
            return normalizeAssistantContent(raw)
        }

        /**
         * Retries the assistant response after a repetition-guard failure.
         */
        suspend fun retryAfterRepetition(token: CancellationToken? = null): String =
            normalizeAssistantContent(runRepetitionGuardRetry(token))

        /**
         * When the assistant returns a blank response but tool calls were made, this sends a
         * follow-up request asking the LLM to generate a user-facing answer from the tool results.
         * Returns the follow-up text, or null if no tool events were recorded.
         */
        suspend fun completeToolOnlyTurnWithFollowup(
            requestId: String,
            token: CancellationToken? = null,
        ): String? {
            val toolEvents = conversationOps.finishedToolEventsByRequestId.remove(requestId).orEmpty()
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
                            add(Message("system", conversationOps.buildMainSystemContextPrompt()))
                            add(
                                Message(
                                    "system",
                                    "Generate the final user-facing answer from the tool results below. Be concrete and do not ask for more context.",
                                ),
                            )
                            addAll(conversationOps.loadRecentHistoryFromDb())
                            add(Message("assistant", "Tool results:\n$summary"))
                        },
                    enabledTools = emptySet(),
                    metadata = mapOf("sessionId" to mainSessionId, "agent" to "main", "requestId" to "$requestId:finalize"),
                    cancellationToken = token,
                )
            token?.throwIfCancelled()
            return llmProvider
                .chat(followup)
                .message.content
                .trim()
        }

        private fun isToolCallsOnlyPayload(text: String): Boolean {
            val compact = text.replace(Regex("\\s+"), "")
            return compact == """{"tool_calls":[]}""" ||
                compact == """```json{"tool_calls":[]}```""" ||
                compact == """```{"tool_calls":[]}```"""
        }

        private suspend fun runRepetitionGuardRetry(token: CancellationToken? = null): String {
            var retryRaw = ""
            repeat(repetitionGuardRetryLimit) {
                token?.throwIfCancelled()
                val base = conversationOps.buildMainRequest(conversationOps.loadRecentHistoryFromDb(), null, token)
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
