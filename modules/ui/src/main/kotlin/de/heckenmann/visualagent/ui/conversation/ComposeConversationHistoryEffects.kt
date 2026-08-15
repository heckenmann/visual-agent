@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.conversation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
internal fun ConversationHistoryPagingEffect(
    state: ConversationUiState,
    listState: LazyListState,
    gateway: ConversationHistoryGateway,
) {
    LaunchedEffect(state, gateway, listState) {
        coroutineScope {
            snapshotFlow { listState.isAtOldestConversationEnd() && !state.sending }
                .distinctUntilChanged()
                .collect { reachedOldest ->
                    if (reachedOldest) {
                        launch {
                            while (listState.isAtOldestConversationEnd() && loadOlderConversationPage(state, gateway)) {
                                withFrameNanos { }
                            }
                        }
                    }
                }
        }
    }
}

private suspend fun loadOlderConversationPage(
    state: ConversationUiState,
    gateway: ConversationHistoryGateway,
): Boolean {
    val request = state.beginOlderRequest() ?: return false
    try {
        val page = gateway.older(request.offset)
        val added = state.applyOlder(request, page)
        return added > 0
    } finally {
        state.finishOlderRequest(request)
    }
}

@Composable
internal fun ConversationScrollToLatestArea(
    isAtLatest: Boolean,
    state: ConversationUiState,
    gateway: ConversationHistoryGateway,
    listState: LazyListState,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        AnimatedVisibility(
            visible = !isAtLatest,
            enter = fadeIn(tween(180)) + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut(tween(180)) + slideOutVertically(targetOffsetY = { it / 2 }),
        ) {
            ScrollToBottomButton(
                onClick = {
                    val request = state.beginLatestRequest()
                    scope.launch {
                        val page = gateway.latest()
                        if (state.applyLatest(request, page)) {
                            withFrameNanos { }
                            listState.jumpToLatest()
                        }
                    }
                },
                modifier = Modifier.padding(end = 12.dp, bottom = 12.dp),
            )
        }
    }
}
