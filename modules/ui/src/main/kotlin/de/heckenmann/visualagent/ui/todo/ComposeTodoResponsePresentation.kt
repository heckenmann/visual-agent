@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.todo

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.TodoItem
import de.heckenmann.visualagent.ui.components.ComposeMarkdown
import de.heckenmann.visualagent.ui.components.labelizeEnumName
import de.heckenmann.visualagent.ui.modal.ComposeContentModal
import de.heckenmann.visualagent.ui.modal.ComposeModalRequester
import de.heckenmann.visualagent.ui.modal.modalDialogLayout
import de.heckenmann.visualagent.ui.modal.modalPrimaryButton

/** Maximum number of response lines rendered in a compact todo card. */
internal const val TODO_RESPONSE_PREVIEW_MAX_LINES = 4

/** Mutable, execution-correlated response state shared by todo presentations. */
@Stable
internal class TodoResponseState {
    var executionId: String? by mutableStateOf(null)
        private set
    var agentId: String? by mutableStateOf(null)
        private set
    var text: String by mutableStateOf("")
        private set

    /** Applies one server progress event, replacing stale output from an older execution. */
    fun apply(
        executionId: String?,
        agentId: String?,
        delta: String,
        completed: Boolean,
    ) {
        if (executionId != null && executionId != this.executionId) {
            this.executionId = executionId
            text = ""
        }
        if (agentId != null) this.agentId = agentId
        if (delta.isNotEmpty()) text += delta
        if (completed) this.executionId = executionId ?: this.executionId
    }

    /** Clears the current execution while retaining the state object for open overlays. */
    fun clear() {
        executionId = null
        agentId = null
        text = ""
    }

    /** Restores persisted output when a conversation panel is opened. */
    fun restore(
        response: String,
        restoredAgentId: String? = null,
    ) {
        executionId = null
        agentId = restoredAgentId
        text = response
    }
}

/** Returns a bounded tail of a response for compact cards and list rows. */
internal fun todoResponseTail(
    response: String,
    maxLines: Int = TODO_RESPONSE_PREVIEW_MAX_LINES,
): String {
    val normalized = response.replace("\r\n", "\n").replace('\r', '\n')
    val lines = normalized.lines()
    val tail = lines.takeLast(maxLines.coerceAtLeast(1))
    return (if (lines.size > tail.size) "…\n" else "") + tail.joinToString("\n")
}

/** Renders the current end of a todo response in a live, single-line window. */
@androidx.compose.runtime.Composable
internal fun TodoResponseSingleLine(
    responseState: TodoResponseState,
    modifier: Modifier = Modifier,
) {
    if (responseState.text.isBlank()) return
    val line = todoStreamingLine(responseState.text)
    val textStyle = MaterialTheme.typography.bodySmall
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    var availableWidthPx by remember { mutableIntStateOf(0) }
    val displayedText =
        remember(line, availableWidthPx, textStyle, density.density, density.fontScale, layoutDirection) {
            fittedTextSuffix(line, availableWidthPx) { candidate ->
                textMeasurer.measure(AnnotatedString(candidate), style = textStyle).size.width
            }
        }
    Box(
        modifier = modifier.onSizeChanged { availableWidthPx = it.width },
        contentAlignment = Alignment.CenterEnd,
    ) {
        if (availableWidthPx > 0 && displayedText.isNotBlank()) {
            Text(
                text = displayedText,
                style = textStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

/** Renders a compact animated indicator while a todo is actively executing. */
@androidx.compose.runtime.Composable
internal fun TodoWorkingIndicator(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "todo-working")
    Row(
        modifier = modifier.semantics { contentDescription = "Todo working" },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse,
                        initialStartOffset = StartOffset(index * 160),
                    ),
                label = "todo-working-dot-$index",
            )
            Box(
                modifier =
                    Modifier
                        .size(5.dp)
                        .alpha(alpha)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
    }
}

/** Shows the complete response in the shared internal todo response overlay. */
@androidx.compose.runtime.Composable
internal fun TodoResponseOverlay(
    todo: TodoItem,
    responseState: TodoResponseState,
    onDismiss: () -> Unit,
) {
    modalDialogLayout(
        body = {
            Text(todo.description, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${todo.status.name.labelizeEnumName()}${responseState.agentId?.let { " · $it" } ?: ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SelectionContainer {
                ComposeMarkdown(responseState.text.ifBlank { "No response output yet." })
            }
        },
        footer = {
            modalPrimaryButton(label = "Close", onClick = onDismiss)
        },
    )
}

/** Opens the shared full-response overlay for a todo from any presentation surface. */
internal fun ComposeModalRequester.requestTodoResponse(
    todo: TodoItem,
    responseState: TodoResponseState,
    currentTodo: () -> TodoItem? = { todo },
) {
    request(
        ComposeContentModal(title = "Todo response") { dismiss ->
            TodoResponseOverlay(todo = currentTodo() ?: todo, responseState = responseState, onDismiss = dismiss)
        },
    )
}
