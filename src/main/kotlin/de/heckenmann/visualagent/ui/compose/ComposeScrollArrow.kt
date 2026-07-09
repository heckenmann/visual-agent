@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Scrolls the provided [scrollState] by [direction] * [SCROLL_ARROW_STEP_PX], clamped to the
 * scrollable range. The scroll is skipped when [isClosing] is true so that no coroutine is launched
 * while the application is shutting down.
 *
 * @param direction Negative for left, positive for right
 * @param scrollState Horizontal scroll state to mutate
 * @param scope Coroutine scope used for the animation
 * @param isClosing Returns true when the application is shutting down
 */
internal fun scrollArrowHandler(
    direction: Int,
    scrollState: LazyListState,
    scope: CoroutineScope,
    isClosing: () -> Boolean,
) {
    if (isClosing()) return
    scope.launch {
        if (isClosing()) return@launch
        scrollState.scrollBy(direction * SCROLL_ARROW_STEP_PX.toFloat())
    }
}

/**
 * Starts a continuous scroll on [scrollState] in the given [direction] and returns the [Job] that
 * performs the scroll. The job repeats until it is cancelled or the scroll value can no longer
 * move in the requested direction. Each step scrolls by [SCROLL_ARROW_STEP_PX] pixels with a short
 * delay between repeats. The first step is executed immediately so the user gets immediate
 * feedback.
 *
 * @param direction Negative for left, positive for right
 * @param scrollState Horizontal scroll state to mutate
 * @param scope Coroutine scope used to run the repeating scroll job
 * @param isClosing Returns true when the application is shutting down
 * @return The continuous scroll job, which the caller must cancel on pointer release
 */
internal fun startContinuousScroll(
    direction: Int,
    scrollState: LazyListState,
    scope: CoroutineScope,
    isClosing: () -> Boolean,
): Job {
    return scope.launch {
        while (isActive && !isClosing()) {
            val consumed = scrollState.scrollBy(direction * SCROLL_ARROW_STEP_PX.toFloat())
            if (consumed == 0f) {
                return@launch
            }
            delay(SCROLL_ARROW_REPEAT_DELAY_MS)
        }
    }
}

/**
 * Renders a directional arrow that scrolls the workspace row when clicked or pressed and held.
 *
 * A short click animates the [scrollState] by a fixed step in the requested direction. Pressing and
 * holding the arrow starts a continuous scroll that repeats until the pointer is released or the
 * scroll value can no longer move in the requested direction. The scroll action is guarded by
 * [isClosing] to avoid launching a coroutine after shutdown has started.
 *
 * @param direction Negative for left, positive for right
 * @param scrollState Horizontal scroll state to mutate
 * @param isClosing Returns true when the application is shutting down and pointer events should be
 *   ignored
 * @param modifier Modifier applied to the arrow root
 */
@Composable
internal fun ScrollArrow(
    direction: Int,
    scrollState: LazyListState,
    isClosing: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val continuousScrollJob = remember { mutableStateOf<Job?>(null) }
    val icon = if (direction < 0) Icons.AutoMirrored.Filled.KeyboardArrowLeft else Icons.AutoMirrored.Filled.KeyboardArrowRight
    val description = if (direction < 0) "Scroll left" else "Scroll right"
    Box(
        modifier =
            modifier
                .padding(horizontal = 2.dp)
                .pointerInput(direction, scrollState, scope) {
                    detectTapGestures(
                        onPress = { _ ->
                            if (isClosing()) return@detectTapGestures
                            val releasedQuickly =
                                withTimeoutOrNull(SCROLL_ARROW_PRESS_HOLD_THRESHOLD_MS) { tryAwaitRelease() } ?: false
                            if (!releasedQuickly) {
                                val job = startContinuousScroll(direction, scrollState, scope, isClosing)
                                continuousScrollJob.value = job
                                tryAwaitRelease()
                                continuousScrollJob.value?.cancel()
                                continuousScrollJob.value = null
                            }
                        },
                        onTap = {
                            scrollArrowHandler(direction, scrollState, scope, isClosing)
                        },
                    )
                }.defaultMinSize(minWidth = 44.dp, minHeight = 64.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0xCC / 255f))
                .border(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0x55 / 255f), RoundedCornerShape(10.dp))
                .semantics(mergeDescendants = true) {
                    contentDescription = description
                },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.defaultMinSize(minWidth = 32.dp, minHeight = 32.dp),
            tint = LocalContentColor.current,
        )
    }
}

private const val SCROLL_ARROW_STEP_PX = 120
private const val SCROLL_ARROW_REPEAT_DELAY_MS = 120L
private const val SCROLL_ARROW_PRESS_HOLD_THRESHOLD_MS = 250L
