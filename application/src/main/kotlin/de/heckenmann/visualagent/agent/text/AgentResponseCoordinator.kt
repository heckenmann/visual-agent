package de.heckenmann.visualagent.agent.text

import de.heckenmann.visualagent.agent.CancellationToken
import de.heckenmann.visualagent.agent.ConversationOpsProvider
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.Message

/**
 * Coordinates assistant response normalization, repetition-guard retries, and tool-only followup finalization.
 */
class AgentResponseCoordinator
    constructor(
        private val llmProvider: LLMProvider,
        private val conversationOps: ConversationOpsProvider,
    ) {
        private val repetitionGuardRetryLimit = 1

        /**
         * Normalizes assistant response content: trims whitespace, replaces blank or tool-calls-only
         * payloads with a placeholder, and detects runaway repetition.
         */
        fun normalizeAssistantContent(content: String): String {
            val trimmed = removeThinkingMarkup(content).trim()
            if (trimmed.isBlank() || isToolCallsOnlyPayload(trimmed)) {
                return "(No text response. See tool results above.)"
            }
            if (ResponseRepetitionGuard.isRunawayRepetition(trimmed)) {
                return "I generated a malformed repeated output. Please repeat your last request and I will answer it cleanly."
            }
            return trimmed
        }

        /**
         * Normalizes assistant content for conversation presentation while retaining reasoning
         * markup for the UI. Provider-facing history must use [normalizeAssistantContent], which
         * removes the markup before sending the conversation back to a model.
         *
         * @param content Raw assistant content, potentially containing `<think>` blocks
         * @return Trimmed presentation content with reasoning markup retained
         */
        fun normalizeAssistantPresentationContent(content: String): String {
            val trimmed = content.trim()
            if (trimmed.isBlank()) return "(No text response. See tool results above.)"
            val withoutThinking = removeThinkingMarkup(trimmed).trim()
            if (withoutThinking.isBlank() || isToolCallsOnlyPayload(withoutThinking)) {
                return if (hasThinkingMarkup(trimmed)) trimmed else "(No text response. See tool results above.)"
            }
            if (ResponseRepetitionGuard.isRunawayRepetition(withoutThinking)) {
                return "I generated a malformed repeated output. Please repeat your last request and I will answer it cleanly."
            }
            return trimmed
        }

        /**
         * Removes model reasoning markup before a message is included in a provider prompt.
         *
         * @param content Message content that may contain reasoning markup
         * @return Content without complete or partial reasoning tags
         */
        fun removeThinkingMarkup(content: String): String =
            content
                .replace(THINKING_BLOCK_PATTERN, "")
                .replace(UNFINISHED_THINKING_BLOCK_PATTERN, "")
                .replace(CLOSE_THINKING_TAG_PATTERN, "")

        private fun hasThinkingMarkup(content: String): Boolean =
            OPEN_THINKING_TAG_PATTERN.containsMatchIn(content) || CLOSE_THINKING_TAG_PATTERN.containsMatchIn(content)

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
                    .chat(conversationOps.buildMainRequest(conversationOps.loadMainAgentContextFromDb(), requestId, token))
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
            val base =
                conversationOps.buildMainRequest(
                    conversationOps.loadMainAgentContextFromDb(),
                    "$requestId:finalize",
                    token,
                )
            val followupMessages = base.messages.toMutableList()
            val systemContextIndex = followupMessages.indexOfFirst { it.role == "system" }
            val instructionIndex = if (systemContextIndex >= 0) systemContextIndex + 1 else 0
            followupMessages.add(
                instructionIndex,
                Message(
                    "system",
                    "Generate the final user-facing answer from the tool results below. Be concrete and do not ask for more context.",
                ),
            )
            followupMessages += Message("assistant", "Tool results:\n$summary")
            val followup = base.copy(messages = followupMessages, enabledTools = emptySet(), cancellationToken = token)
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

        private companion object {
            private val THINKING_BLOCK_PATTERN = Regex("(?is)<think>.*?</think>")
            private val OPEN_THINKING_TAG_PATTERN = Regex("(?i)<think>")
            private val UNFINISHED_THINKING_BLOCK_PATTERN = Regex("(?is)<think>.*$")
            private val CLOSE_THINKING_TAG_PATTERN = Regex("(?i)</think>")
        }

        private suspend fun runRepetitionGuardRetry(token: CancellationToken? = null): String {
            var retryRaw = ""
            repeat(repetitionGuardRetryLimit) {
                token?.throwIfCancelled()
                val base = conversationOps.buildMainRequest(conversationOps.loadMainAgentContextFromDb(), null, token)
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
