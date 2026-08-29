@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.todo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.ui.modal.ComposeModalRequester

/**
 * Shows the latest response text while a todo is being processed.
 *
 * The row expands and contracts vertically while the newest response window remains visible.
 *
 * @param visible Whether the todo is currently being processed
 * @param responseState Complete response state received so far
 */
@Composable
internal fun TodoStreamingResponse(
    visible: Boolean,
    working: Boolean,
    responseState: TodoResponseState,
    modalRequester: ComposeModalRequester,
    todo: de.heckenmann.visualagent.protocol.TodoItem,
    currentTodo: () -> de.heckenmann.visualagent.protocol.TodoItem? = { todo },
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (working) TodoWorkingIndicator()
                if (responseState.text.isBlank()) {
                    Text(
                        text = "Waiting for response…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                } else {
                    TodoResponseSingleLine(
                        responseState = responseState,
                        modifier =
                            Modifier
                                .weight(1f)
                                .clickable { modalRequester.requestTodoResponse(todo, responseState, currentTodo) },
                    )
                }
            }
        }
    }
}
