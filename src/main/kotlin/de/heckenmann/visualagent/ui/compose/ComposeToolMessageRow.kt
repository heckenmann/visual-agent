@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandCircleDown
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.agent.Message
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

@Composable
internal fun ToolMessageRow(
    message: Message,
    isDeleting: Boolean,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metadata = remember(message.metadata) { parseToolMetadata(message.metadata) }
    var expanded by remember { mutableStateOf(false) }
    AnimatedVisibility(
        visible = !isDeleting,
        enter = fadeIn(),
        exit = fadeOut(animationSpec = tween(DELETE_ANIMATION_DURATION_MS)),
        modifier = modifier.fillMaxWidth().animateContentSize(),
    ) {
        PanelContentCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = MaterialTheme.colorScheme.surface,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "TOOL · ${metadata.toolId}",
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = metadata.durationMillis?.let { "${it}ms" } ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (message.id != null) {
                    ActionIconButton(
                        icon = Icons.Filled.Delete,
                        description = "Delete tool call",
                        modifier = Modifier.size(28.dp),
                        onClick = onDelete,
                    )
                }
                ActionIconButton(
                    icon = Icons.Filled.ExpandCircleDown,
                    description = if (expanded) "Collapse tool details" else "Expand tool details",
                    modifier = Modifier.size(28.dp),
                    onClick = { expanded = !expanded },
                )
            }
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = if (metadata.status == "error") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            if (expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 8.dp)) {
                    metadata.inputJson?.takeIf { it.isNotBlank() }?.let {
                        DetailBlock(label = "Input", content = it)
                    }
                    metadata.resultContent?.takeIf { it.isNotBlank() }?.let {
                        DetailBlock(label = "Result", content = it)
                    }
                    metadata.resultError?.takeIf { it.isNotBlank() }?.let {
                        DetailBlock(label = "Error", content = it)
                    }
                }
            }
        }
    }
}

internal data class ParsedToolMetadata(
    val toolId: String,
    val status: String,
    val durationMillis: Long?,
    val inputJson: String?,
    val resultContent: String?,
    val resultError: String?,
)

internal fun parseToolMetadata(metadata: String?): ParsedToolMetadata {
    val json =
        metadata
            ?.let {
                runCatching {
                    kotlinx.serialization.json.Json
                        .parseToJsonElement(it)
                        .jsonObject
                }.getOrNull()
            }
    return ParsedToolMetadata(
        toolId = json?.get("toolId")?.jsonPrimitive?.content ?: "tool",
        status = json?.get("status")?.jsonPrimitive?.content ?: "ok",
        durationMillis = json?.get("durationMillis")?.jsonPrimitive?.longOrNull,
        inputJson = json?.get("inputJson")?.jsonPrimitive?.content,
        resultContent = json?.get("resultContent")?.jsonPrimitive?.content,
        resultError = json?.get("resultError")?.jsonPrimitive?.content,
    )
}

@Composable
internal fun SubAgentMessageRow(
    message: Message,
    isDeleting: Boolean,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metadata = remember(message.metadata) { parseSubAgentMetadata(message.metadata) }
    var expanded by remember { mutableStateOf(false) }
    AnimatedVisibility(
        visible = !isDeleting,
        enter = fadeIn(),
        exit = fadeOut(animationSpec = tween(DELETE_ANIMATION_DURATION_MS)),
        modifier = modifier.fillMaxWidth().animateContentSize(),
    ) {
        PanelContentCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = MaterialTheme.colorScheme.surface,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "AGENT · ${metadata.agentName ?: "sub-agent"}",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (metadata.success) "completed" else "failed",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (metadata.success) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
                if (message.id != null) {
                    ActionIconButton(
                        icon = Icons.Filled.Delete,
                        description = "Delete sub-agent message",
                        modifier = Modifier.size(28.dp),
                        onClick = onDelete,
                    )
                }
                ActionIconButton(
                    icon = Icons.Filled.ExpandCircleDown,
                    description = if (expanded) "Collapse sub-agent details" else "Expand sub-agent details",
                    modifier = Modifier.size(28.dp),
                    onClick = { expanded = !expanded },
                )
            }
            if (expanded) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

private data class ParsedSubAgentMetadata(
    val jobId: String,
    val success: Boolean,
    val agentId: String?,
    val agentName: String?,
)

private fun parseSubAgentMetadata(metadata: String?): ParsedSubAgentMetadata {
    val json =
        metadata
            ?.let {
                runCatching {
                    kotlinx.serialization.json.Json
                        .parseToJsonElement(it)
                }.getOrNull()
            }?.jsonObject
    return ParsedSubAgentMetadata(
        jobId = json?.get("jobId")?.jsonPrimitive?.content ?: "",
        success = json?.get("success")?.jsonPrimitive?.booleanOrNull ?: false,
        agentId = json?.get("agentId")?.jsonPrimitive?.content,
        agentName = json?.get("agentName")?.jsonPrimitive?.content,
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
