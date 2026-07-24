@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import de.heckenmann.visualagent.agent.Message

/**
 * Scrolls the conversation list to the bottom once on composition when history is not empty.
 */
@Composable
internal fun ConversationStartupScrollEffect(
    history: List<Message>,
    listState: LazyListState,
) {
    LaunchedEffect(Unit) {
        if (history.isNotEmpty()) {
            listState.scrollToItem(history.lastIndex)
        }
    }
}

/**
 * Keeps the conversation list scrolled to the bottom when the last message changes.
 *
 * Uses [LazyListState.requestScrollToItem] (non-suspending) so the scroll is
 * posted as a request and processed in the next layout pass without blocking
 * the composition or the chunk callback.
 */
@Composable
internal fun ConversationScrollOnChangeEffect(
    history: List<Message>,
    listState: LazyListState,
) {
    val lastMessageKey = history.lastOrNull()?.let { "${it.id}:${it.content.hashCode()}" }
    LaunchedEffect(lastMessageKey) {
        if (history.isNotEmpty()) {
            listState.requestScrollToItem(history.lastIndex)
        }
    }
}
