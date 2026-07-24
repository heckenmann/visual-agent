@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.agent.Message
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Tertiary conversation row for a tool call.
 *
 * Renders as a compact inline chip with the tool name and duration. The full
 * input, result, and error content is collapsed by default. When the tool is
 * currently executing the chip shows an animated spinner and "running…" text.
 *
 * @param message Conversation message carrying tool metadata
 * @param isDeleting Whether the row is currently animating out
 * @param isInFlight Whether the tool call is currently executing
 * @param onDelete Callback invoked when delete is requested
 * @param modifier Modifier applied to the row
 */
@Composable
internal fun ToolMessageRow(
    message: Message,
    isDeleting: Boolean,
    isInFlight: Boolean,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metadata = remember(message.metadata) { parseToolMetadata(message.metadata) }
    var expanded by remember { mutableStateOf(false) }
    val error = metadata.status == "error"
    val tint =
        if (error) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    AnimatedVisibility(
        visible = !isDeleting,
        enter = fadeIn(),
        exit = fadeOut(animationSpec = tween(DELETE_ANIMATION_DURATION_MS)),
        modifier = modifier.fillMaxWidth().animateContentSize(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest, shape = MaterialTheme.shapes.small)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isInFlight) {
                    ToolInFlightSpinner(modifier = Modifier.size(14.dp))
                } else {
                    Icon(
                        imageVector = if (error) Icons.Filled.Delete else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = tint,
                    )
                }
                Text(
                    text = metadata.toolId,
                    style = MaterialTheme.typography.labelMedium,
                    color = tint,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text =
                        if (isInFlight) {
                            "running…"
                        } else {
                            metadata.durationMillis?.let { "${it}ms" } ?: ""
                        },
                    style = MaterialTheme.typography.labelSmall,
                    color = tint,
                )
                if (message.id != null) {
                    ActionIconButton(
                        icon = Icons.Filled.Delete,
                        description = "Delete tool call",
                        modifier = Modifier.size(22.dp),
                        onClick = onDelete,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse tool details" else "Expand tool details",
                    modifier = Modifier.size(18.dp).alpha(0.7f),
                    tint = tint,
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(animationSpec = tween(180)),
                exit = fadeOut(animationSpec = tween(180)),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
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
