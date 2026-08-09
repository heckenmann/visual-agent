@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.conversation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
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
import java.time.Instant
import java.util.UUID

/**
 * Source of a queued message.
 */
enum class QueuedMessageSource {
    /** Message typed by the user in the conversation input. */
    USER,

    /** Notification that a sub-agent completed or cancelled a todo. */
    TODO_RETURN,
}

/**
 * A message waiting in the queue to be delivered to the main agent.
 *
 * @property id Unique identifier
 * @property content Message text
 * @property source Origin of the message
 * @property queuedAt Timestamp when the message was enqueued
 * @property todoId Todo identifier when [source] is [QueuedMessageSource.TODO_RETURN]
 * @property agentId Sub-agent identifier when [source] is [QueuedMessageSource.TODO_RETURN]
 */
data class QueuedMessage(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val source: QueuedMessageSource,
    val queuedAt: Instant = Instant.now(),
    val todoId: String? = null,
    val agentId: String? = null,
)

/**
 * Flush mode controlling how queued messages are delivered.
 */
enum class QueueFlushMode {
    /** Each queued message is sent as a separate request. */
    ONE_BY_ONE,

    /** All queued messages are combined into a single request. */
    ALL_AT_ONCE,
}

/**
 * In-memory message queue holder for the conversation panel.
 *
 * The queue is transient and not persisted. Todo-return notifications are
 * already persisted as conversation messages regardless of queue state.
 */
class MessageQueue {
    /** Ordered list of queued messages. */
    val messages = mutableStateListOf<QueuedMessage>()

    /** Whether the queue is currently being flushed. */
    var flushing: Boolean = false

    /** Current flush mode. */
    var flushMode: QueueFlushMode = QueueFlushMode.ONE_BY_ONE

    /** Enqueues a message and returns its id. */
    fun enqueue(
        content: String,
        source: QueuedMessageSource,
        todoId: String? = null,
        agentId: String? = null,
    ): String {
        val msg =
            QueuedMessage(
                content = content,
                source = source,
                todoId = todoId,
                agentId = agentId,
            )
        messages.add(msg)
        return msg.id
    }

    /** Removes a message by id. Returns true if removed. */
    fun remove(id: String): Boolean = messages.removeAll { it.id == id }

    /** Removes all messages. */
    fun clear() {
        messages.clear()
    }

    /** Returns the first message without removing it. */
    fun peek(): QueuedMessage? = messages.firstOrNull()

    /** Removes and returns the first message. */
    fun dequeue(): QueuedMessage? = if (messages.isEmpty()) null else messages.removeAt(0)

    /** Whether the queue has any messages. */
    val isNotEmpty: Boolean get() = messages.isNotEmpty()

    /** Number of queued messages. */
    val size: Int get() = messages.size
}

/**
 * Queue strip displayed above the text input field when messages are queued.
 *
 * Shows queued message previews with source icon, truncated content, and
 * interrupt/send-now button. Includes flush-mode toggle and clear-all button.
 *
 * @param queue The message queue holder
 * @param onSendNow Callback to interrupt and send a specific queued message
 * @param onClear Callback to clear all queued messages
 * @param onToggleFlushMode Callback to toggle between one-by-one and all-at-once
 */
@Composable
internal fun MessageQueueStrip(
    queue: MessageQueue,
    onSendNow: (QueuedMessage) -> Unit,
    onClear: () -> Unit,
    onToggleFlushMode: () -> Unit,
) {
    AnimatedVisibility(
        visible = queue.isNotEmpty,
        enter = fadeIn(animationSpec = tween(180)) + slideInVertically(initialOffsetY = { -it / 2 }),
        exit = fadeOut(animationSpec = tween(180)) + slideOutVertically(targetOffsetY = { -it / 2 }),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Queued (${queue.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text =
                            "Flush: ${if (queue.flushMode == QueueFlushMode.ONE_BY_ONE) "one-by-one" else "all-at-once"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier =
                            Modifier
                                .clickable { onToggleFlushMode() }
                                .padding(horizontal = 4.dp),
                    )
                    ActionIconButton(
                        icon = Icons.Filled.Close,
                        description = "Clear queue",
                        onClick = onClear,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                queue.messages.forEach { msg ->
                    QueuedMessageChip(
                        message = msg,
                        onSendNow = { onSendNow(msg) },
                    )
                }
            }
        }
    }
}

@Composable
private fun QueuedMessageChip(
    message: QueuedMessage,
    onSendNow: () -> Unit,
) {
    val (icon, label) =
        when (message.source) {
            QueuedMessageSource.USER -> Icons.Filled.Person to "User"
            QueuedMessageSource.TODO_RETURN -> Icons.Filled.TaskAlt to "Todo"
        }
    Row(
        modifier =
            Modifier
                .widthIn(max = 220.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 6.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = message.content,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        ActionIconButton(
            icon = Icons.AutoMirrored.Filled.Send,
            description = "Send now",
            onClick = onSendNow,
            modifier = Modifier.size(16.dp),
        )
    }
}
