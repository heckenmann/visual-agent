@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.conversation

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.IntSize
import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.CancellationToken
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.conversation.ConversationHistoryPage
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
import kotlinx.coroutines.withContext

@Composable
internal fun ConversationOlderHistoryLoader(
    isAtEnd: Boolean,
    history: List<de.heckenmann.visualagent.agent.Message>,
    listState: LazyListState,
    agentManager: AgentManager,
    onHistoryChange: (List<de.heckenmann.visualagent.agent.Message>) -> Unit,
    onLoadStateChange: (Boolean) -> Unit = {},
    onHasMoreHistoryChange: (Boolean) -> Unit = {},
    paginationResetVersion: Int = 0,
    scrollCoordinator: ConversationScrollCoordinator? = null,
) {
    val state = remember { ConversationUiState(history) }
    val coordinator = scrollCoordinator ?: remember(listState) { ConversationScrollCoordinator(listState) }
    val gateway =
        remember(agentManager) {
            object : ConversationHistoryGateway {
                override suspend fun latest(): ConversationHistoryPage =
                    ConversationHistoryPage(agentManager.getHistory(), offset = 0, hasMore = true)

                override suspend fun older(offset: Int): ConversationHistoryPage {
                    val messages = withContext(Dispatchers.IO) { agentManager.loadOlderHistory() }
                    return ConversationHistoryPage(messages, offset = offset, hasMore = messages.isNotEmpty())
                }
            }
        }
    val timeline = buildConversationTimeline(state.history, null, "", false, state.isLoadingOlder, false)
    LaunchedEffect(paginationResetVersion) {
        if (paginationResetVersion > 0) {
            val request = state.beginLatestRequest()
            state.applyLatest(request, gateway.latest())
        }
    }
    LaunchedEffect(state.history) { onHistoryChange(state.history) }
    LaunchedEffect(state.isLoadingOlder) { onLoadStateChange(state.isLoadingOlder) }
    LaunchedEffect(state.hasMoreHistory) { onHasMoreHistoryChange(state.hasMoreHistory) }
    ConversationHistoryPagingEffect(isAtEnd, state, timeline, listState, gateway, coordinator)
}

@Composable
internal fun ConversationScrollToBottomArea(
    isAtBottom: Boolean,
    listState: LazyListState,
    scope: CoroutineScope,
    agentManager: AgentManager? = null,
    onHistoryRefresh: () -> Unit = {},
    bottomPadding: Int = 0,
    modifier: Modifier = Modifier,
    scrollCoordinator: ConversationScrollCoordinator? = null,
) {
    val state = remember { ConversationUiState(agentManager?.getHistory().orEmpty()) }
    val coordinator = scrollCoordinator ?: remember(listState) { ConversationScrollCoordinator(listState) }
    val gateway =
        remember(agentManager) {
            object : ConversationHistoryGateway {
                override suspend fun latest(): ConversationHistoryPage {
                    agentManager?.refreshHistoryToLatest()
                    onHistoryRefresh()
                    return ConversationHistoryPage(agentManager?.getHistory().orEmpty(), offset = 0, hasMore = true)
                }

                override suspend fun older(offset: Int): ConversationHistoryPage =
                    ConversationHistoryPage(emptyList(), offset = offset, hasMore = false)
            }
        }
    ConversationScrollToLatestArea(isAtBottom, state, gateway, coordinator, scope, modifier)
}

@Composable
internal fun ConversationResizeScrollEffect(
    panelSize: IntSize,
    inputAreaHeight: Int,
    hasConversationContent: Boolean,
    listState: LazyListState,
    scrollCoordinator: ConversationScrollCoordinator? = null,
) {
    ConversationResizeScrollEffect(panelSize, hasConversationContent, listState, scrollCoordinator)
}

internal suspend fun executeSend(
    content: String,
    agentManager: AgentManager,
    inFlight: InFlightStateHolder,
    inputFocusRequester: FocusRequester,
    onInputChange: (String) -> Unit,
    onSendingChange: (Boolean) -> Unit,
    onStatusChange: (String) -> Unit,
    onHistoryChange: (List<Message>) -> Unit,
    onActiveTokenChange: (CancellationToken?) -> Unit,
    onPendingUserMessageChange: (String?) -> Unit,
    streamingFlow: MutableStateFlow<String>,
) = executeSend(
    content,
    AgentManagerConversationGateway(agentManager),
    inFlight,
    inputFocusRequester,
    onInputChange,
    onSendingChange,
    onStatusChange,
    onHistoryChange,
    onActiveTokenChange,
    onPendingUserMessageChange,
    streamingFlow,
)

@Composable
internal fun ConversationQueueFlushEffect(
    sending: Boolean,
    inFlight: InFlightStateHolder,
    queue: MessageQueue,
    agentManager: AgentManager,
    inputFocusRequester: FocusRequester,
    onInputChange: (String) -> Unit,
    onSendingChange: (Boolean) -> Unit,
    onStatusChange: (String) -> Unit,
    onHistoryChange: (List<Message>) -> Unit,
    onActiveTokenChange: (CancellationToken?) -> Unit,
    onPendingUserMessageChange: (String?) -> Unit,
    streamingFlow: MutableStateFlow<String>,
) = ConversationQueueFlushEffect(
    sending,
    inFlight,
    queue,
    AgentManagerConversationGateway(agentManager),
    inputFocusRequester,
    onInputChange,
    onSendingChange,
    onStatusChange,
    onHistoryChange,
    onActiveTokenChange,
    onPendingUserMessageChange,
    streamingFlow,
)
