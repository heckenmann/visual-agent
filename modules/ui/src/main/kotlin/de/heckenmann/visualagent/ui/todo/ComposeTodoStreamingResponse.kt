@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.todo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.ui.conversation.StreamingText
import kotlinx.coroutines.flow.collectLatest

private const val TODO_RESPONSE_WINDOW_CHARS = 180

/**
 * Shows the latest response text while a todo is being processed.
 *
 * The row expands and contracts vertically, while each new one-line response window
 * enters from the right and pushes the previous window out to the left.
 *
 * @param visible Whether the todo is currently being processed
 * @param response Complete response text received so far
 */
@Composable
internal fun TodoStreamingResponse(
    visible: Boolean,
    response: String,
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
                    .padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            StreamingText(text = response, charsPerTick = 1) { displayedText ->
                val window = todoResponseWindow(displayedText)
                if (window.isBlank()) {
                    Text(
                        text = "Waiting for response…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                } else {
                    val scrollState = rememberScrollState()
                    LaunchedEffect(Unit) {
                        snapshotFlow { scrollState.maxValue }.collectLatest { maxValue ->
                            scrollState.scrollTo(maxValue)
                        }
                    }
                    LaunchedEffect(window) {
                        scrollState.scrollTo(scrollState.maxValue)
                    }
                    Text(
                        text = window,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.fillMaxWidth().horizontalScroll(scrollState, enabled = false),
                    )
                }
            }
        }
    }
}

/**
 * Keeps only the newest response characters for the fixed-width streaming window.
 *
 * @param response Complete response text received so far
 * @param maxChars Maximum number of visible characters
 * @return Newest response suffix
 */
internal fun todoResponseWindow(
    response: String,
    maxChars: Int = TODO_RESPONSE_WINDOW_CHARS,
): String = response.takeLast(maxChars.coerceAtLeast(1))
