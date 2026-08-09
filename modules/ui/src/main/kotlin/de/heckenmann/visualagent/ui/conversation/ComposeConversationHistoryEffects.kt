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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.launch

@Composable
internal fun ConversationHistoryPagingEffect(
    isAtOldest: Boolean,
    state: ConversationUiState,
    timeline: List<ConversationTimelineItem>,
    listState: LazyListState,
    gateway: ConversationHistoryGateway,
    scrollCoordinator: ConversationScrollCoordinator,
) {
    val currentTimeline by rememberUpdatedState(timeline)
    state.updateOldestPosition(isAtOldest)
    LaunchedEffect(isAtOldest) {
        if (!isAtOldest) return@LaunchedEffect
        val request = state.beginOlderRequest() ?: return@LaunchedEffect
        val anchorGeneration = scrollCoordinator.beginHistoryAnchorRestore()
        val visible = listState.layoutInfo.visibleItemsInfo
        val knownKeys = timeline.mapTo(mutableSetOf(), ConversationTimelineItem::stableKey)
        val anchor =
            visible.firstOrNull {
                it.key in knownKeys && it.key != ConversationTimelineItem.OlderHistoryLoading.stableKey
            } ?: visible.firstOrNull()
        try {
            val page = gateway.older(request.offset)
            val added = state.applyOlder(request, page)
            if (added > 0 && anchor != null) {
                withFrameNanos { }
                val keyedTarget = currentTimeline.indexOfFirst { it.stableKey == anchor.key }
                val targetIndex = if (keyedTarget >= 0) keyedTarget else anchor.index + added
                scrollCoordinator.completeHistoryAnchorRestore(anchorGeneration, targetIndex, anchor.offset)
            }
        } finally {
            state.finishOlderRequest(request)
            scrollCoordinator.finishHistoryAnchorRestore(anchorGeneration)
        }
    }
}

@Composable
internal fun ConversationScrollToLatestArea(
    isAtLatest: Boolean,
    state: ConversationUiState,
    gateway: ConversationHistoryGateway,
    scrollCoordinator: ConversationScrollCoordinator,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    var pendingNavigationGeneration by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(pendingNavigationGeneration, state.history) {
        val generation = pendingNavigationGeneration ?: return@LaunchedEffect
        scrollCoordinator.completeJumpToLatest(generation)
        if (pendingNavigationGeneration == generation) {
            pendingNavigationGeneration = null
        }
    }
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
                        val navigationGeneration = scrollCoordinator.beginJumpToLatest()
                        val page = gateway.latest()
                        if (state.applyLatest(request, page)) {
                            pendingNavigationGeneration = navigationGeneration
                        }
                    }
                },
                modifier = Modifier.padding(end = 12.dp, bottom = 12.dp),
            )
        }
    }
}
