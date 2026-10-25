package de.heckenmann.visualagent.ui.conversation.live

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.model.ToolLiveState
import de.heckenmann.visualagent.ui.components.ActionIconButton

/** Compact text has visual line limits; expansion exposes only the entries actually retained by the runtime. */
@Composable
internal fun ToolLiveTextSection(state: ToolLiveState) {
    var expanded by remember(state.executionId) { mutableStateOf(false) }
    var statusOverflow by remember(state.executionId) { mutableStateOf(false) }
    var logOverflow by remember(state.executionId) { mutableStateOf(false) }
    Column {
        Row {
            Box(Modifier.weight(1f)) {
                if (expanded) {
                    Text(state.status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LiveAnimatedText(
                        state.status,
                        state.executionId,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        terminal = state.terminal,
                        onTextLayout = { statusOverflow = it.hasVisualOverflow },
                    )
                }
            }
            Box(Modifier.size(32.dp)) {
                if (expanded || statusOverflow || (state.logs.isNotEmpty() && logOverflow)) {
                    ActionIconButton(
                        icon = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        description = if (expanded) "Collapse live details" else "Expand live details",
                        onClick = { expanded = !expanded },
                    )
                }
            }
        }
        if (state.logs.isNotEmpty()) {
            if (expanded) {
                Text("${state.logs.size} retained log entries", style = MaterialTheme.typography.labelSmall)
                Box(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                    SelectionContainer { Text(state.logs.joinToString("\n"), style = MaterialTheme.typography.bodySmall) }
                }
            } else {
                ToolLiveLogPreview(state) { logOverflow = it }
            }
        }
    }
}
