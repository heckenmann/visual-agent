@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.runtime.Composable
import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.CancellationToken
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.conversation.WelcomeResult
import de.heckenmann.visualagent.error.ErrorMessageMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Extracted conversation panel actions to keep [ConversationPanel] under the 300-LOC limit.
 */
internal fun handleClearConversation(
    scope: CoroutineScope,
    modalRequester: ComposeModalRequester,
    agentManager: AgentManager,
    activeToken: () -> CancellationToken?,
    onSendingChange: (Boolean) -> Unit,
    onStatusChange: (String) -> Unit,
    onHistoryRefresh: () -> Unit,
) {
    modalRequester.requestConfirmation(
        ComposeConfirmationModal(
            title = "Clear conversation?",
            message =
                "This will stop any active request, cancel all running sub-agent jobs and open todos, " +
                    "then remove the persisted conversation history.",
            confirmDescription = "Clear conversation",
        ) {
            scope.launch {
                onSendingChange(true)
                onStatusChange("Stopping active work and clearing conversation...")
                activeToken()?.cancel()
                agentManager.cancelAllRunningActions()
                agentManager.cancelAllActiveTodos()
                runCatching {
                    agentManager.clearHistory()
                    agentManager.addWelcomeMessageAfterReset()
                }.onSuccess { result ->
                    onStatusChange(
                        when (result) {
                            is WelcomeResult.Generated -> "Conversation cleared"
                            is WelcomeResult.Fallback ->
                                "Welcome could not be generated: ${result.error.message ?: result.error::class.simpleName.orEmpty()}"
                        },
                    )
                }.onFailure { error ->
                    onStatusChange("Welcome could not be generated: ${error.message ?: error::class.simpleName.orEmpty()}")
                }
                onHistoryRefresh()
                onSendingChange(false)
            }
        },
    )
}

@Composable
internal fun ConversationEditModal(
    editingId: String?,
    history: List<Message>,
    agentManager: AgentManager,
    onDismiss: () -> Unit,
    onHistoryRefresh: () -> Unit,
) {
    if (editingId == null) return
    val message = history.find { it.id == editingId } ?: return
    EditMessageModal(
        content = message.content,
        onDismiss = onDismiss,
        onSave = { newContent ->
            editingId.let { id ->
                agentManager.updateMessageContentById(id, newContent)
                onHistoryRefresh()
            }
            onDismiss()
        },
    )
}

/**
 * Executes a single message send: streams the response, updates state, and handles errors.
 */
internal suspend fun executeSend(
    content: String,
    agentManager: AgentManager,
    inFlight: InFlightStateHolder,
    inputFocusRequester: androidx.compose.ui.focus.FocusRequester,
    onInputChange: (String) -> Unit,
    onSendingChange: (Boolean) -> Unit,
    onStatusChange: (String) -> Unit,
    onHistoryChange: (List<Message>) -> Unit,
    onActiveTokenChange: (CancellationToken?) -> Unit,
) {
    onInputChange("")
    onSendingChange(true)
    onStatusChange("Streaming...")
    onHistoryChange(agentManager.getHistory() + Message("user", content))
    val streamRequestId =
        java.util.UUID
            .randomUUID()
            .toString()
    val token = CancellationToken()
    onActiveTokenChange(token)
    inFlight.markStreamStart(streamRequestId)
    val streamedContent = StringBuilder()
    val result =
        runCatching {
            agentManager.streamMessage(content, token) { chunk ->
                streamedContent.append(chunk)
                onHistoryChange(agentManager.getHistory().dropLast(1) + Message("assistant", streamedContent.toString()))
            }
        }
    result
        .onSuccess {
            onHistoryChange(agentManager.getHistory())
            onStatusChange("Ready")
        }.onFailure {
            onHistoryChange(agentManager.getHistory())
            val userError = ErrorMessageMapper.map(it)
            onStatusChange("${userError.summary}: ${userError.detail}")
        }.also {
            inFlight.markStreamEnd(streamRequestId)
            onSendingChange(false)
            onActiveTokenChange(null)
            inputFocusRequester.requestFocus()
        }
}
