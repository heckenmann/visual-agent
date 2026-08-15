@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.conversation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.TodoItem
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

/**
 * Inline conversation indicator showing that a todo is currently being processed.
 *
 * @param todo Todo that is in progress
 * @param agentName Display name of the assigned sub-agent, if known
 * @param modifier Modifier applied to the indicator row
 */
@Composable
internal fun TodoInProgressRow(
    todo: TodoItem,
    agentName: String?,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "todo-progress")
    val pulse by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "todo-progress-pulse",
    )
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .alpha(pulse)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
        )
        Text(
            text = "Todo \"${todo.description}\" in progress${agentName?.let { " — Agent \"$it\"" } ?: ""}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * Inline animated chip shown while a sub-agent is running.
 *
 * @param agentName Display name of the running sub-agent
 * @param modifier Modifier applied to the chip
 */
@Composable
internal fun SubAgentRunningChip(
    agentName: String,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "sub-agent-running")
    val pulse by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "sub-agent-running-pulse",
    )
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(6.dp)
                    .alpha(pulse)
                    .background(MaterialTheme.colorScheme.tertiary, CircleShape),
        )
        Text(
            text = "Agent \"$agentName\" is working…",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Animated spinner used on a tool-call chip while the tool is executing.
 *
 * @param modifier Modifier applied to the spinner icon
 */
@Composable
internal fun ToolInFlightSpinner(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "tool-spinner")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "tool-spinner-rotation",
    )
    Icon(
        imageVector = Icons.Filled.Refresh,
        contentDescription = "Tool running",
        modifier = modifier.rotate(rotation),
        tint = MaterialTheme.colorScheme.primary,
    )
}

/**
 * Animated left-edge accent bar for the assistant row that is actively being streamed.
 *
 * @param isActive Whether the streaming accent should be visible
 * @param modifier Modifier applied to the bar container
 */
@Composable
internal fun StreamingAccentBar(
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = isActive,
        enter = fadeIn(animationSpec = tween(180)),
        exit = fadeOut(animationSpec = tween(180)),
        modifier = modifier,
    ) {
        val transition = rememberInfiniteTransition(label = "streaming-accent")
        val alpha by transition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "streaming-accent-alpha",
        )
        Box(
            modifier =
                Modifier
                    .width(2.dp)
                    .alpha(alpha)
                    .background(MaterialTheme.colorScheme.primary),
        )
    }
}
