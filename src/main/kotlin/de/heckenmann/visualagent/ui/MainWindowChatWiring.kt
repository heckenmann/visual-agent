package de.heckenmann.visualagent.ui

import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.provider.ProviderErrorMessages
import de.heckenmann.visualagent.config.AppConfig
import de.heckenmann.visualagent.ui.panels.ChatPanel
import javafx.application.Platform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Wires conversation panel callbacks to the agent manager.
 *
 * @property agentManager Main application orchestrator
 * @property chatPanel Conversation UI panel
 * @property scope UI coroutine scope
 * @property openTodos Action that switches to the todo panel
 */
internal class MainWindowChatWiring(
    private val agentManager: AgentManager,
    private val chatPanel: ChatPanel,
    private val scope: CoroutineScope,
    private val openTodos: () -> Unit,
) {
    private var lastToolResultPreview: String? = null
    private val thinkBlockRegex = Regex("<think>(.*?)</think>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    /**
     * Registers chat send, clear, todo navigation, and history loading callbacks.
     */
    fun register() {
        chatPanel.setOnSendMessage { text -> sendMessage(text) }
        chatPanel.setOnClearConversation { clearConversation() }
        chatPanel.setOnOpenTodos { openTodos() }
        chatPanel.setOnLoadOlderMessages { loadOlderMessages() }
    }

    /**
     * Stores the latest tool result preview used to repair empty/generic model responses.
     *
     * @param preview Compact tool result preview
     */
    fun updateToolPreview(preview: String?) {
        lastToolResultPreview = preview
    }

    private fun sendMessage(text: String) {
        scope.launch {
            val startedAt = System.nanoTime()
            lastToolResultPreview = null
            try {
                val response =
                    if (AppConfig.instance.streamingEnabled) {
                        streamMessage(text)
                    } else {
                        withContext(Dispatchers.IO) { agentManager.sendMessage(text) }
                    }
                finishResponse(response, startedAt)
            } catch (e: Exception) {
                val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
                Platform.runLater {
                    chatPanel.addAssistantMessage(ProviderErrorMessages.userFacing(e))
                    chatPanel.updateResponseMetrics(elapsedMs)
                }
            }
        }
    }

    private suspend fun streamMessage(text: String): String {
        val collected = StringBuilder()
        return withContext(Dispatchers.IO) {
            agentManager.streamMessage(text) { chunk ->
                collected.append(chunk)
                val partialResponse = collected.toString()
                Platform.runLater {
                    chatPanel.updateStreamingAssistantMessage(partialResponse)
                }
            }
        }
    }

    private fun finishResponse(
        response: String,
        startedAt: Long,
    ) {
        val normalizedResponse = normalizeAssistantResponse(response)
        val parsed = extractThinking(normalizedResponse)
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        Platform.runLater {
            if (AppConfig.instance.thinkingEnabled) {
                parsed.thinkingBlocks.forEach { thinking -> chatPanel.addThinkingEvent(thinking) }
            }
            val assistantText =
                parsed.answer.takeIf { it.isNotBlank() } ?: "(No text response. See tool results above.)"
            if (AppConfig.instance.streamingEnabled) {
                chatPanel.finishStreamingAssistantMessage(assistantText)
            } else {
                chatPanel.addAssistantMessage(assistantText)
            }
            chatPanel.updateResponseMetrics(elapsedMs)
        }
    }

    private fun clearConversation() {
        scope.launch {
            Platform.runLater { chatPanel.startAssistantLoading() }
            withContext(Dispatchers.IO) {
                agentManager.clearHistory()
            }
            val welcome =
                withContext(Dispatchers.IO) {
                    agentManager.addWelcomeMessageAfterReset()
                }
            Platform.runLater { chatPanel.addAssistantMessage(welcome) }
        }
    }

    private fun loadOlderMessages() {
        scope.launch {
            val older = withContext(Dispatchers.IO) { agentManager.loadOlderHistory(20) }
            Platform.runLater { chatPanel.prependConversationHistory(older) }
        }
    }

    private fun normalizeAssistantResponse(response: String): String {
        if (response.isBlank()) {
            val preview = lastToolResultPreview
            if (!preview.isNullOrBlank()) {
                return "Ich habe das Tool ausgeführt. Ergebnis: $preview"
            }
        }
        val lower = response.lowercase()
        val isGenericClarification =
            lower.contains("mehr kontext") ||
                lower.contains("nicht eindeutig") ||
                lower.contains("what exactly should i do") ||
                lower.contains("i need more context")
        if (isGenericClarification) {
            val preview = lastToolResultPreview
            if (!preview.isNullOrBlank()) {
                return "Ich habe das Tool bereits ausgeführt. Ergebnis: $preview"
            }
        }
        return response
    }

    /**
     * Extracts `<think>...</think>` blocks from model output and returns clean assistant text.
     *
     * @param response Raw assistant text
     * @return Parsed thinking blocks plus visible answer text
     */
    private fun extractThinking(response: String): ParsedThinking {
        if (response.isBlank()) return ParsedThinking(emptyList(), response)
        val thoughts =
            thinkBlockRegex
                .findAll(response)
                .map {
                    it.groupValues
                        .getOrNull(1)
                        .orEmpty()
                        .trim()
                }.filter { it.isNotBlank() }
                .toList()
        val stripped = thinkBlockRegex.replace(response, "").trim()
        return ParsedThinking(thoughts, stripped)
    }

    /**
     * Parsed response container for optional thinking blocks and assistant-visible answer.
     *
     * @property thinkingBlocks Extracted model thinking snippets
     * @property answer Final user-facing assistant answer
     */
    private data class ParsedThinking(
        val thinkingBlocks: List<String>,
        val answer: String,
    )
}
