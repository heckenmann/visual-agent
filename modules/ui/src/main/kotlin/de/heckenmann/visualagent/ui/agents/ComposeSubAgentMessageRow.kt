@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.agents

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import de.heckenmann.visualagent.protocol.ConversationMessage as Message

/**
 * Secondary conversation row for a sub-agent result.
 *
 * Renders as a compact summary with a vertical accent bar. The full result
 * content is collapsed by default. While the sub-agent is still running an
 * animated "is working…" chip is shown in addition to the summary.
 *
 * @param message Conversation message carrying sub-agent metadata
 * @param isDeleting Whether the row is currently animating out
 * @param isRunning Whether the sub-agent is currently executing a job
 * @param onDelete Callback invoked when delete is requested
 * @param modifier Modifier applied to the row
 */
@Composable
internal fun SubAgentMessageRow(
    message: Message,
    isDeleting: Boolean,
    isRunning: Boolean,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metadata = remember(message.metadata) { parseSubAgentMetadata(message.metadata) }
    var expanded by remember { mutableStateOf(false) }
    val workType = if (metadata.todoId.isNullOrBlank()) "job" else "todo"
    val completion = if (metadata.success) "completed" else "failed"
    val summary = "Agent \"${metadata.agentName ?: "sub-agent"}\" $completion a $workType"
    val accentColor =
        if (metadata.success) {
            MaterialTheme.colorScheme.tertiary
        } else {
            MaterialTheme.colorScheme.error
        }
    AnimatedVisibility(
        visible = !isDeleting,
        enter = fadeIn(),
        exit = fadeOut(animationSpec = tween(DELETE_ANIMATION_DURATION_MS)),
        modifier = modifier.fillMaxWidth().animateContentSize(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest, shape = MaterialTheme.shapes.small)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .width(2.dp)
                        .background(accentColor)
                        .padding(vertical = 2.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (message.id != null) {
                        ActionIconButton(
                            icon = Icons.Filled.Delete,
                            description = "Delete sub-agent message",
                            modifier = Modifier.size(22.dp),
                            onClick = onDelete,
                        )
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Collapse sub-agent details" else "Expand sub-agent details",
                        modifier = Modifier.size(18.dp).alpha(0.7f),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isRunning) {
                    SubAgentRunningChip(agentName = metadata.agentName ?: "sub-agent")
                }
                AnimatedVisibility(
                    visible = expanded,
                    enter = fadeIn(animationSpec = tween(180)),
                    exit = fadeOut(animationSpec = tween(180)),
                ) {
                    SelectionContainer {
                        ComposeMarkdown(message.content)
                    }
                }
            }
        }
    }
}

internal data class ParsedSubAgentMetadata(
    val jobId: String,
    val success: Boolean,
    val agentId: String?,
    val agentName: String?,
    val todoId: String?,
    val hasCompletionStatus: Boolean,
)

internal fun parseSubAgentMetadata(metadata: String?): ParsedSubAgentMetadata {
    val json =
        metadata
            ?.let {
                runCatching {
                    kotlinx.serialization.json.Json
                        .parseToJsonElement(it)
                        .jsonObject
                }.getOrNull()
            }
    return ParsedSubAgentMetadata(
        jobId = json?.get("jobId")?.jsonPrimitive?.content ?: "",
        success = json?.get("success")?.jsonPrimitive?.booleanOrNull ?: false,
        agentId = json?.get("agentId")?.jsonPrimitive?.content,
        agentName = json?.get("agentName")?.jsonPrimitive?.content,
        todoId = json?.get("todoId")?.jsonPrimitive?.content,
        hasCompletionStatus = json?.get("success") != null,
    )
}

@Composable
internal fun DetailBlock(
    label: String,
    content: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = content,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
