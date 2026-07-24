@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import de.heckenmann.visualagent.agent.Message
import kotlinx.coroutines.delay

/** Debounce delay before auto-scrolling so layout info is stable after content changes. */
internal const val SCROLL_DEBOUNCE_MS = 50L

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
 * Reacting to the last message identity and content (not just the list size) ensures
 * streaming responses, which mutate the last assistant item in place, still keep the
 * latest content visible.
 */
@Composable
internal fun ConversationScrollOnChangeEffect(
    history: List<Message>,
    listState: LazyListState,
) {
    val lastMessageKey = history.lastOrNull()?.let { "${it.id}:${it.content.hashCode()}" }
    LaunchedEffect(lastMessageKey) {
        if (history.isNotEmpty()) {
            delay(SCROLL_DEBOUNCE_MS)
            listState.scrollToItem(history.lastIndex)
        }
    }
}
