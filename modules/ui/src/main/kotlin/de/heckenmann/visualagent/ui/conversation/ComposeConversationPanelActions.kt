@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.conversation

import androidx.compose.runtime.Composable
import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.CancellationToken
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.conversation.WelcomeResult
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.error.ErrorMessageMapper
import de.heckenmann.visualagent.ui.agents.*
import de.heckenmann.visualagent.ui.application.*
import de.heckenmann.visualagent.ui.canvas.*
import de.heckenmann.visualagent.ui.components.*
import de.heckenmann.visualagent.ui.conversation.*
import de.heckenmann.visualagent.ui.files.*
import de.heckenmann.visualagent.ui.modal.*
import de.heckenmann.visualagent.ui.settings.*
import de.heckenmann.visualagent.ui.status.*
import de.heckenmann.visualagent.ui.todo.*
import de.heckenmann.visualagent.ui.workspace.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Persist conversation placement without blocking the Compose main dispatcher. */
internal suspend fun persistConversationInputPlacement(config: AppConfigBean) {
    withContext(Dispatchers.IO) {
        config.save()
    }
}

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
 *
 * Streaming runs on [Dispatchers.IO] so the UI thread is never blocked. The streaming
 * content is pushed to a [MutableStateFlow] (thread-safe, non-blocking via [tryEmit]).
 * Compose collects it via [collectAsState] on the Main dispatcher, fully decoupled from
 * the streaming coroutine. [onHistoryChange] is only refreshed from DB after streaming
 * completes, so the LazyColumn is not rebuilt on every token.
 */
internal suspend fun executeSend(
    content: String,
    messageGateway: ConversationMessageGateway,
    inFlight: InFlightStateHolder,
    inputFocusRequester: androidx.compose.ui.focus.FocusRequester,
    onInputChange: (String) -> Unit,
    onSendingChange: (Boolean) -> Unit,
    onStatusChange: (String) -> Unit,
    onHistoryChange: (List<Message>) -> Unit,
    onActiveTokenChange: (CancellationToken?) -> Unit,
    onPendingUserMessageChange: (String?) -> Unit,
    streamingFlow: MutableStateFlow<String>,
) {
    onInputChange("")
    onSendingChange(true)
    onStatusChange("Streaming...")
    onPendingUserMessageChange(content)
    streamingFlow.value = ""
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
            messageGateway.stream(content, token) { chunk ->
                streamedContent.append(chunk)
                streamingFlow.value = streamedContent.toString()
            }
        }
    streamingFlow.value = ""
    onPendingUserMessageChange(null)
    onHistoryChange(messageGateway.currentHistory())
    result
        .onSuccess {
            onHistoryChange(messageGateway.currentHistory())
            onStatusChange("Ready")
        }.onFailure {
            onHistoryChange(messageGateway.currentHistory())
            val userError = ErrorMessageMapper.map(it)
            onStatusChange("${userError.summary}: ${userError.detail}")
        }.also {
            inFlight.markStreamEnd(streamRequestId)
            onSendingChange(false)
            onActiveTokenChange(null)
            inputFocusRequester.requestFocus()
        }
}
