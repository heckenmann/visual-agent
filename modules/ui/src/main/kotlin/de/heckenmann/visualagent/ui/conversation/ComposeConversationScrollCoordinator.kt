@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.conversation

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
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
import kotlinx.coroutines.flow.distinctUntilChanged

/** Scrolls a reverse-layout conversation list to its visual newest end. */
internal suspend fun LazyListState.scrollToBottom() {
    if (layoutInfo.totalItemsCount == 0) return
    scrollToItem(0)
}

/** Describes whether conversation scrolling follows latest content or preserves user browsing. */
internal enum class ConversationScrollMode {
    FOLLOWING_LATEST,
    BROWSING_HISTORY,
    JUMPING_TO_LATEST,
    RESTORING_HISTORY_ANCHOR,
}

/**
 * Serializes conversation scroll policy around one [LazyListState].
 *
 * User navigation invalidates pending explicit jumps. Reverse-layout details remain
 * confined to this coordinator so callers express intent instead of target indices.
 */
internal class ConversationScrollCoordinator(
    private val listState: LazyListState,
) {
    var mode: ConversationScrollMode by mutableStateOf(ConversationScrollMode.FOLLOWING_LATEST)
        private set

    private var navigationGeneration = 0L
    private var userScrollStart: ConversationListPosition? = null
    private var userScrollMoved = false

    /** Marks the start of deliberate user navigation and invalidates pending jumps. */
    fun onUserScrollStarted() {
        navigationGeneration++
        userScrollStart = currentPosition()
        userScrollMoved = false
    }

    /** Marks user input as browsing only after it actually moves the list. */
    fun onUserScrollMoved() {
        userScrollMoved = true
        mode =
            if (currentPosition().isAtLatest) {
                ConversationScrollMode.FOLLOWING_LATEST
            } else {
                ConversationScrollMode.BROWSING_HISTORY
            }
    }

    /** Restores follow mode when user navigation reaches the newest end. */
    fun updateLatestPosition(
        isAtLatest: Boolean,
        position: ConversationListPosition,
    ) {
        val start = userScrollStart
        if (start != null && position != start) {
            userScrollMoved = true
            if (!isAtLatest) {
                mode = ConversationScrollMode.BROWSING_HISTORY
            }
        }
        if (isAtLatest && mode == ConversationScrollMode.BROWSING_HISTORY && userScrollMoved) {
            mode = ConversationScrollMode.FOLLOWING_LATEST
            userScrollStart = null
        }
    }

    private fun currentPosition(): ConversationListPosition =
        ConversationListPosition(
            index = listState.firstVisibleItemIndex,
            offset = listState.firstVisibleItemScrollOffset,
            canScrollBackward = listState.canScrollBackward,
        )

    /** Stops an active gesture, starts an explicit jump, and returns its completion generation. */
    suspend fun beginJumpToLatest(): Long {
        listState.stopScroll(MutatePriority.PreventUserInput)
        navigationGeneration++
        mode = ConversationScrollMode.JUMPING_TO_LATEST
        return navigationGeneration
    }

    /** Positions initial content at the reverse-layout newest end. */
    suspend fun showInitialLatest() {
        mode = ConversationScrollMode.FOLLOWING_LATEST
        withFrameNanos { }
        listState.scrollToBottom()
    }

    /** Follows newly laid-out content only while latest content is being followed. */
    suspend fun followLatestContentChange() {
        if (mode != ConversationScrollMode.FOLLOWING_LATEST) return
        withFrameNanos { }
        if (mode == ConversationScrollMode.FOLLOWING_LATEST) {
            listState.scrollToBottom()
        }
    }

    /** Completes an explicit latest jump unless newer user navigation invalidated it. */
    suspend fun completeJumpToLatest(generation: Long): Boolean {
        if (generation != navigationGeneration) return finishInvalidatedJump()
        // History replacement and Markdown measurement are not reflected in layout until the next frame.
        // Jumping before that frame lets LazyColumn retain its previous key anchor after scrollToItem(0).
        withFrameNanos { }
        if (generation != navigationGeneration) return finishInvalidatedJump()
        listState.scrollToBottom()
        if (generation != navigationGeneration) return finishInvalidatedJump()
        mode = ConversationScrollMode.FOLLOWING_LATEST
        return true
    }

    private fun finishInvalidatedJump(): Boolean {
        mode =
            if (currentPosition().isAtLatest) {
                ConversationScrollMode.FOLLOWING_LATEST
            } else {
                ConversationScrollMode.BROWSING_HISTORY
            }
        return false
    }

    /** Keeps latest content visible after resize without overriding user movement. */
    suspend fun maintainLatestAfterViewportChange() {
        if (mode != ConversationScrollMode.FOLLOWING_LATEST) return
        withFrameNanos { }
        if (mode == ConversationScrollMode.FOLLOWING_LATEST) {
            listState.scrollToBottom()
        }
    }

    /** Starts restoring the visible anchor after an older-history page is loaded. */
    fun beginHistoryAnchorRestore(): Long {
        navigationGeneration++
        mode = ConversationScrollMode.RESTORING_HISTORY_ANCHOR
        return navigationGeneration
    }

    /** Restores an older-history anchor unless user navigation invalidated the request. */
    suspend fun completeHistoryAnchorRestore(
        generation: Long,
        index: Int,
        offset: Int,
    ): Boolean {
        if (generation != navigationGeneration) return false
        listState.scrollToItem(index, offset)
        if (generation != navigationGeneration) return false
        mode = ConversationScrollMode.BROWSING_HISTORY
        return true
    }

    /** Leaves anchor-restoration mode when loading finishes without a position change. */
    fun finishHistoryAnchorRestore(generation: Long) {
        if (generation == navigationGeneration && mode == ConversationScrollMode.RESTORING_HISTORY_ANCHOR) {
            mode = ConversationScrollMode.BROWSING_HISTORY
        }
    }
}

/** Exposes semantic viewport positions without leaking reverse-layout calculations. */
internal class ConversationViewport(
    private val listState: LazyListState,
) {
    val isAtLatest: Boolean
        get() = !listState.canScrollBackward

    val isAtOldest: Boolean
        get() {
            val layoutInfo = listState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
            return lastVisibleItem != null && lastVisibleItem.index == layoutInfo.totalItemsCount - 1
        }
}

/** Gives deliberate wheel and drag navigation priority over pending automatic scrolls. */
internal class ConversationUserScrollConnection(
    private val coordinator: ConversationScrollCoordinator,
) : NestedScrollConnection {
    override fun onPreScroll(
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (source == NestedScrollSource.UserInput && available != Offset.Zero) {
            coordinator.onUserScrollStarted()
        }
        return Offset.Zero
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (source == NestedScrollSource.UserInput && consumed != Offset.Zero) {
            coordinator.onUserScrollMoved()
        }
        return Offset.Zero
    }
}

@Composable
internal fun ConversationLatestPositionEffect(
    listState: LazyListState,
    coordinator: ConversationScrollCoordinator,
) {
    LaunchedEffect(listState, coordinator) {
        snapshotFlow {
            val position =
                ConversationListPosition(
                    index = listState.firstVisibleItemIndex,
                    offset = listState.firstVisibleItemScrollOffset,
                    canScrollBackward = listState.canScrollBackward,
                )
            position to coordinator.mode
        }.distinctUntilChanged()
            .collect { (position, _) ->
                coordinator.updateLatestPosition(
                    isAtLatest = position.isAtLatest,
                    position = position,
                )
            }
    }
}

internal data class ConversationListPosition(
    val index: Int,
    val offset: Int,
    val canScrollBackward: Boolean,
) {
    val isAtLatest: Boolean
        get() = index == 0 && offset == 0 && !canScrollBackward
}
