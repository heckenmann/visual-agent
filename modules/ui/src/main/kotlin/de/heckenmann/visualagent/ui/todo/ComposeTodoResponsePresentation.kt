@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.todo

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.TodoItem
import de.heckenmann.visualagent.ui.components.ActionIconButton
import de.heckenmann.visualagent.ui.components.ComposeMarkdown
import de.heckenmann.visualagent.ui.components.labelizeEnumName
import de.heckenmann.visualagent.ui.modal.ComposeContentModal
import de.heckenmann.visualagent.ui.modal.ComposeModalRequester

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
    Text(
        text = todoResponseWindow(responseState.text),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        modifier = modifier.fillMaxWidth(),
    )
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
    val responseScrollState = rememberScrollState()
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 560.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(todo.description, style = MaterialTheme.typography.titleMedium)
        Text(
            text = "${todo.status.name.labelizeEnumName()}${responseState.agentId?.let { " · $it" } ?: ""}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            SelectionContainer {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(responseScrollState).padding(end = 14.dp),
                ) {
                    ComposeMarkdown(responseState.text.ifBlank { "No response output yet." })
                }
            }
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(responseScrollState),
                modifier =
                    Modifier
                        .align(Alignment.CenterEnd)
                        .semantics { contentDescription = "Todo response scrollbar" },
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            ActionIconButton(icon = Icons.Filled.Close, description = "Close todo response", onClick = onDismiss)
        }
    }
}

/** Opens the shared full-response overlay for a todo from any presentation surface. */
internal fun ComposeModalRequester.requestTodoResponse(
    todo: TodoItem,
    responseState: TodoResponseState,
) {
    request(
        ComposeContentModal(title = "Todo response") { dismiss ->
            TodoResponseOverlay(todo = todo, responseState = responseState, onDismiss = dismiss)
        },
    )
}
