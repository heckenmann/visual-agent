@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.conversation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import de.heckenmann.visualagent.protocol.CancellationToken
import de.heckenmann.visualagent.protocol.CancellationTokenImpl
import de.heckenmann.visualagent.protocol.ConversationPort
import de.heckenmann.visualagent.protocol.ConversationStreamRequest
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
import de.heckenmann.visualagent.protocol.ConversationMessage as Message

/** Persist conversation placement without blocking the Compose main dispatcher. */
internal suspend fun persistConversationInputPlacement(
    conversationPort: ConversationPort,
    placement: de.heckenmann.visualagent.protocol.ConversationInputPlacement,
) {
    withContext(Dispatchers.IO) {
        val current = conversationPort.preferences()
        conversationPort.updatePreferences(current.copy(inputPlacement = placement))
    }
}

/** Returns a non-blocking callback that updates and persists the composer placement. */
internal fun conversationInputPlacementChange(
    scope: CoroutineScope,
    conversationPort: ConversationPort,
    onPlacementChanged: (de.heckenmann.visualagent.protocol.ConversationInputPlacement) -> Unit,
): (de.heckenmann.visualagent.protocol.ConversationInputPlacement) -> Unit =
    { placement ->
        onPlacementChanged(placement)
        scope.launch { persistConversationInputPlacement(conversationPort, placement) }
    }

/**
 * Extracted conversation panel actions to keep [ConversationPanel] under the 300-LOC limit.
 */
internal fun handleClearConversation(
    scope: CoroutineScope,
    modalRequester: ComposeModalRequester,
    conversationPort: ConversationPort,
    activeToken: () -> CancellationToken?,
    onSendingChange: (Boolean) -> Unit,
    onStatusChange: (String) -> Unit,
    onHistoryRefresh: suspend () -> Unit,
    onTodosCleared: () -> Unit,
) {
    modalRequester.requestConfirmation(
        ComposeConfirmationModal(
            title = "Clear conversation?",
            message =
                "This will stop any active request and running sub-agent jobs, delete all todos, " +
                    "then remove the persisted conversation history.",
            confirmDescription = "Clear conversation",
        ) {
            scope.launch {
                onSendingChange(true)
                onStatusChange("Stopping active work and clearing conversation...")
                activeToken()?.cancel()
                conversationPort.cancelActiveWork()
                runCatching {
                    conversationPort.clearAndCreateWelcome()
                }.onSuccess { result ->
                    onTodosCleared()
                    onStatusChange(result.warning?.let { "Welcome could not be generated: $it" } ?: "Conversation cleared")
                }.onFailure { error ->
                    onStatusChange("Welcome could not be generated: ${error.message ?: error::class.simpleName.orEmpty()}")
                }
                onHistoryRefresh()
                onSendingChange(false)
            }
        },
    )
}

/**
 * Queues a user message and clears the composer only after the queue accepted it.
 *
 * Keeping the composer content on failure lets the user retry without retyping the message.
 */
internal fun queueUserMessage(
    content: String,
    enqueue: (String) -> Unit,
    queuedMessageCount: () -> Int,
    onInputChange: (String) -> Unit,
    onStatusChange: (String) -> Unit,
) {
    runCatching { enqueue(content) }
        .onSuccess {
            onInputChange("")
            onStatusChange("Queued (${queuedMessageCount()})")
        }.onFailure {
            onStatusChange("Could not queue message: ${it.toUiErrorMessage()}")
        }
}

@Composable
internal fun ConversationEditModal(
    editingId: String?,
    history: List<Message>,
    conversationPort: ConversationPort,
    modalRequester: ComposeModalRequester,
    onDismiss: () -> Unit,
    onHistoryRefresh: suspend () -> Unit,
) {
    if (editingId == null) return
    val message = history.find { it.id == editingId } ?: return
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(editingId) {
        modalRequester.request(
            ComposeContentModal(
                title = "Edit message",
                content = { dismiss ->
                    EditMessageForm(
                        content = message.content,
                        onDismiss = dismiss,
                        onSave = { newContent ->
                            scope.launch {
                                withContext(Dispatchers.IO) { conversationPort.updateMessage(editingId, newContent) }
                                onHistoryRefresh()
                                dismiss()
                            }
                        },
                    )
                },
                onDismiss = onDismiss,
            ),
        )
    }
}

/**
 * Executes a single message send: streams the response, updates state, and handles errors.
 *
 * Streaming runs on [Dispatchers.IO] so the UI thread is never blocked. The streaming
 * content is pushed to a [MutableStateFlow] (thread-safe, non-blocking via [tryEmit]).
 * Compose collects it via [collectAsState] on the Main dispatcher, fully decoupled from
 * the streaming coroutine. [onStreamCompletion] applies the persisted history only after
 * streaming completes, so the LazyColumn is not rebuilt on every token.
 */
internal suspend fun executeSend(
    content: String,
    messageGateway: ConversationMessageGateway,
    inFlight: InFlightStateHolder,
    inputFocusRequester: androidx.compose.ui.focus.FocusRequester,
    onInputChange: (String) -> Unit,
    onSendingChange: (Boolean) -> Unit,
    onStatusChange: (String) -> Unit,
    onActiveTokenChange: (CancellationToken?) -> Unit,
    onPendingUserMessageChange: (String?) -> Unit,
    onPendingUserEntryIdChange: (String?) -> Unit,
    onStreamingEntryIdChange: (String?) -> Unit,
    onStreamCompletion: (List<Message>) -> Unit,
    streamingFlow: MutableStateFlow<String>,
) {
    onInputChange("")
    onSendingChange(true)
    onStatusChange("Streaming...")
    val userEntryId =
        java.util.UUID
            .randomUUID()
            .toString()
    val assistantEntryId =
        java.util.UUID
            .randomUUID()
            .toString()
    onPendingUserMessageChange(content)
    onPendingUserEntryIdChange(userEntryId)
    onStreamingEntryIdChange(assistantEntryId)
    streamingFlow.value = ""
    val streamRequestId =
        java.util.UUID
            .randomUUID()
            .toString()
    val token = CancellationTokenImpl()
    onActiveTokenChange(token)
    inFlight.markStreamStart(streamRequestId)
    val streamedContent = StringBuilder()
    val result =
        runCatching {
            messageGateway.stream(ConversationStreamRequest(userEntryId, assistantEntryId, content), token) { chunk ->
                streamedContent.append(chunk)
                streamingFlow.value = streamedContent.toString()
            }
        }
    val completedHistory = messageGateway.currentHistory()
    onStreamCompletion(completedHistory)
    result
        .onSuccess {
            onStatusChange("Ready")
        }.onFailure {
            onStatusChange(it.toUiErrorMessage())
        }.also {
            inFlight.markStreamEnd(streamRequestId)
            onSendingChange(false)
            onActiveTokenChange(null)
            inputFocusRequester.requestFocus()
        }
}
