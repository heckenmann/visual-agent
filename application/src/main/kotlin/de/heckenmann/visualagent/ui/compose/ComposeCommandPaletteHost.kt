@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun ComposeCommandPaletteHost(
    visible: Boolean,
    commands: List<ComposeCommand>,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    val scheme = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    val matches = filterCommands(commands, query)
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(scheme.scrim.copy(alpha = 0xB8 / 255f))
                .padding(24.dp)
                .onPreviewKeyEvent { event ->
                    when {
                        event.type == KeyEventType.KeyDown && event.key == Key.Escape -> {
                            onDismiss()
                            true
                        }
                        event.type == KeyEventType.KeyDown && event.key == Key.Enter -> {
                            matches.firstOrNull()?.run {
                                action()
                                onDismiss()
                                true
                            } ?: false
                        }
                        else -> false
                    }
                }.focusRequester(focusRequester)
                .focusable(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Card(
            modifier =
                Modifier
                    .widthIn(min = 420.dp, max = 680.dp)
                    .padding(top = 72.dp)
                    .border(1.dp, scheme.tertiary.copy(alpha = 0x66 / 255f), RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerHigh),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Filled.Search, contentDescription = null, tint = scheme.tertiary)
                    Text(
                        text = "Command Palette",
                        color = scheme.onSurface,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    ActionIconButton(icon = Icons.Filled.Close, description = "Close command palette", onClick = onDismiss)
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search commands") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (matches.isEmpty()) {
                        Text("No matching commands", color = scheme.primary, style = MaterialTheme.typography.bodyMedium)
                    }
                    matches.forEach { command ->
                        CommandRow(command, scheme) {
                            command.action()
                            onDismiss()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandRow(
    command: ComposeCommand,
    scheme: androidx.compose.material3.ColorScheme,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(1.dp, scheme.outline.copy(alpha = 0x33 / 255f), RoundedCornerShape(14.dp))
                .clickable(onClick = onClick)
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(command.title, color = scheme.onSurface, fontWeight = FontWeight.SemiBold)
        Text(command.description, color = scheme.primary, style = MaterialTheme.typography.bodySmall)
    }
}
