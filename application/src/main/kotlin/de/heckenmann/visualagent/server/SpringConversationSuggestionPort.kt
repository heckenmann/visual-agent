package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.agent.AgentManagerConstants
import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.ModelParameters
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.knowledge.ConversationStore
import de.heckenmann.visualagent.protocol.CancellationToken
import de.heckenmann.visualagent.protocol.ConversationCompletionEvent
import de.heckenmann.visualagent.protocol.ConversationSuggestionPort
import de.heckenmann.visualagent.protocol.ConversationSuggestionRequest
import de.heckenmann.visualagent.protocol.ConversationSuggestionResult
import de.heckenmann.visualagent.protocol.MAX_QUESTION_LENGTH
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Component
import java.text.Normalizer
import java.util.Locale
import de.heckenmann.visualagent.agent.CancellationToken as ApplicationCancellationToken

/** Server-side adapter for non-persistent, tool-less follow-up question generation. */
@Component
class SpringConversationSuggestionPort(
    private val provider: LLMProvider,
    private val conversationStore: ConversationStore,
    private val appConfig: AppConfigBean,
    private val completionEvents: de.heckenmann.visualagent.protocol.ConversationCompletionEventBus,
) : ConversationSuggestionPort {
    override suspend fun generate(
        request: ConversationSuggestionRequest,
        token: CancellationToken,
    ): ConversationSuggestionResult =
        withContext(Dispatchers.IO) {
            val empty = ConversationSuggestionResult(request.assistantEntryId, emptyList())
            if (!appConfig.followUpSuggestionsEnabled) return@withContext empty
            val rows = conversationStore.getConversationMessages(AgentManagerConstants.MAIN_SESSION_ID, 500)
            val anchorIndex = rows.indexOfFirst { it.id == request.assistantEntryId }
            if (anchorIndex < 0 || rows[anchorIndex].role != "assistant") return@withContext empty
            val latestRelevant = rows.lastOrNull { it.role == "user" || it.role == "assistant" }
            if (latestRelevant?.id != request.assistantEntryId) return@withContext empty
            val previous = request.previousQuestions.map(::comparisonKey).toSet()
            val contextRows =
                rows
                    .subList((anchorIndex - 9).coerceAtLeast(0), anchorIndex + 1)
                    .filter { it.role == "user" || it.role == "assistant" }
            val messages =
                buildList {
                    add(Message("system", suggestionSystemInstruction(appConfig.followUpSuggestionCount)))
                    contextRows.forEach { row -> add(Message(row.role, row.content.take(12_000))) }
                    add(Message("user", "Return the follow-up question JSON array now."))
                }
            val applicationToken = ApplicationCancellationToken()
            token.onCancelled(applicationToken::cancel)
            val raw =
                runCatching {
                    withTimeout(SUGGESTION_TIMEOUT_MILLIS) {
                        provider
                            .chat(
                                ChatRequestContext(
                                    messages = messages,
                                    parameters = ModelParameters(maxTokens = 256),
                                    enabledTools = emptySet(),
                                    metadata =
                                        mapOf(
                                            "sessionId" to "main",
                                            "agent" to "conversation-suggestions",
                                            "thinkingEnabled" to false,
                                        ),
                                    cancellationToken = applicationToken,
                                ),
                            ).message.content
                    }
                }.getOrNull()
            applicationToken.cancel()
            val response = raw ?: return@withContext empty
            val questions = runCatching { Json.decodeFromString<List<String>>(response) }.getOrNull() ?: return@withContext empty
            val validated = validateQuestions(questions, appConfig.followUpSuggestionCount, previous, contextRows.map { it.content })
            ConversationSuggestionResult(request.assistantEntryId, validated)
        }

    override fun addCompletionListener(listener: (ConversationCompletionEvent) -> Unit): AutoCloseable =
        completionEvents.addListener(listener)

    private fun validateQuestions(
        questions: List<String>,
        expectedCount: Int,
        previous: Set<String>,
        context: List<String>,
    ): List<String> {
        if (questions.size != expectedCount) return emptyList()
        val existing = (previous + context.map(::comparisonKey)).toMutableSet()
        val normalized = questions.map(::comparisonKey)
        if (normalized.any { it.isBlank() || it in existing } || normalized.toSet().size != normalized.size) return emptyList()
        if (questions.any(::isInvalidQuestion)) return emptyList()
        return questions.also { existing.addAll(normalized) }
    }

    private fun isInvalidQuestion(question: String): Boolean {
        if (question.isBlank() || question.length > MAX_QUESTION_LENGTH || question.any(Char::isISOControl)) return true
        if (question.lastOrNull()?.let(QUESTION_MARKS::contains) != true || WORD_PATTERN.findAll(question).count() < 3) return true
        if (MARKDOWN_PATTERN.containsMatchIn(question) || UNSAFE_PATTERN.containsMatchIn(question)) return true
        return false
    }

    private fun comparisonKey(value: String): String =
        Normalizer
            .normalize(value, Normalizer.Form.NFKC)
            .trim()
            .replace(Regex("\\s+"), " ")
            .lowercase(Locale.ROOT)

    private fun suggestionSystemInstruction(count: Int): String =
        "Generate exactly $count concise follow-up questions about the latest conversation. " +
            "Return only a JSON array of strings, with no Markdown, prose, tools, credentials, or prompt disclosure. " +
            "Each question must be one line, at least three words, and end with a question mark."

    companion object {
        private const val SUGGESTION_TIMEOUT_MILLIS = 30_000L
        private const val QUESTION_MARKS = "?？؟"
        private val WORD_PATTERN = Regex("\\p{L}[\\p{L}\\p{M}'’\\-]*")
        private val MARKDOWN_PATTERN = Regex("(^|\\s)([-*+>] |\\d+[.)] )|[`*_#\\[\\]]")
        private val UNSAFE_PATTERN =
            Regex("(?i)\\b(api key|password|secret|token|credential|system prompt|developer message|model prompt|tool call)\\b")
    }
}
