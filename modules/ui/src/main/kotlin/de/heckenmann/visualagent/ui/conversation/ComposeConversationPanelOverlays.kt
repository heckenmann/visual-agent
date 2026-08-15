@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.conversation

import androidx.compose.runtime.Composable
import de.heckenmann.visualagent.protocol.CancellationToken
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Renders the queued-message controls and starts queued requests on demand. */
@Composable
internal fun ConversationPanelQueueStrip(
    queue: MessageQueue,
    scope: CoroutineScope,
    inFlight: InFlightStateHolder,
    messageGateway: ConversationMessageGateway,
    inputFocusRequester: androidx.compose.ui.focus.FocusRequester,
    activeToken: () -> CancellationToken?,
    onInputChange: (String) -> Unit,
    onSendingChange: (Boolean) -> Unit,
    onStatusChange: (String) -> Unit,
    onHistoryChange: (List<de.heckenmann.visualagent.protocol.ConversationMessage>) -> Unit,
    onActiveTokenChange: (CancellationToken?) -> Unit,
    onPendingUserMessageChange: (String?) -> Unit,
    streamingFlow: MutableStateFlow<String>,
) {
    MessageQueueStrip(
        queue = queue,
        onSendNow = { msg ->
            queue.remove(msg.id)
            activeToken()?.cancel()
            scope.launch {
                delay(200)
                executeSend(
                    content = msg.content,
                    messageGateway = messageGateway,
                    inFlight = inFlight,
                    inputFocusRequester = inputFocusRequester,
                    onInputChange = onInputChange,
                    onSendingChange = onSendingChange,
                    onStatusChange = onStatusChange,
                    onHistoryChange = onHistoryChange,
                    onActiveTokenChange = onActiveTokenChange,
                    onPendingUserMessageChange = onPendingUserMessageChange,
                    streamingFlow = streamingFlow,
                )
            }
        },
        onClear = { queue.clear() },
        onToggleFlushMode = {
            queue.flushMode =
                if (queue.flushMode == QueueFlushMode.ONE_BY_ONE) {
                    QueueFlushMode.ALL_AT_ONCE
                } else {
                    QueueFlushMode.ONE_BY_ONE
                }
        },
    )
}
