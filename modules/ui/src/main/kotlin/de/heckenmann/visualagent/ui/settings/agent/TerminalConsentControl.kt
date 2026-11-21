package de.heckenmann.visualagent.ui.settings.agent

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import de.heckenmann.visualagent.protocol.TerminalConsentPort
import de.heckenmann.visualagent.protocol.TerminalConsentSnapshot
import de.heckenmann.visualagent.ui.components.PanelSection
import de.heckenmann.visualagent.ui.components.toUiErrorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shows and confirms consent for one terminal execution host. */
@Composable
internal fun TerminalConsentControl(
    targetLabel: String,
    executionNotice: String,
    grants: List<String>,
    enabled: Boolean,
    canEnable: Boolean,
    saving: Boolean,
    error: String?,
    onChange: (Boolean) -> Unit,
) {
    var confirmEnable by remember(targetLabel) { mutableStateOf(false) }
    PanelSection(targetLabel) {
        Text("Allow agents to run terminal commands")
        Text(executionNotice)
        if (!enabled && !canEnable) Text("Terminal commands cannot be enabled on this server.")
        Switch(
            checked = enabled,
            enabled = !saving && (enabled || canEnable),
            onCheckedChange = { requested ->
                if (requested) confirmEnable = true else onChange(false)
            },
            modifier = Modifier.semantics { contentDescription = "Allow terminal on $targetLabel" },
        )
        if (error != null) Text(error)
    }
    if (confirmEnable && canEnable && !enabled) {
        var acceptedRisks by remember(targetLabel) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { confirmEnable = false },
            title = { Text("Enable terminal on $targetLabel?") },
            text = {
                Column {
                    Text(executionNotice)
                    Text(
                        "Commands can read shared files and can change or permanently delete writable files. " +
                            "Scripts and their child processes have the same access. Command output may expose sensitive data " +
                            "to the model or conversation history. A sandbox flaw may expose other resources available to that account.",
                    )
                    grants.forEach { Text(it) }
                    Row(
                        modifier =
                            Modifier.fillMaxWidth().toggleable(
                                value = acceptedRisks,
                                role = Role.Checkbox,
                                onValueChange = { acceptedRisks = it },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = acceptedRisks, onCheckedChange = null)
                        Text("I understand and accept the risks.")
                    }
                }
            },
            dismissButton = { TextButton(onClick = { confirmEnable = false }) { Text("Cancel") } },
            confirmButton = {
                TextButton(
                    enabled = acceptedRisks && !saving,
                    onClick = {
                        confirmEnable = false
                        onChange(true)
                    },
                ) { Text("Enable terminal") }
            },
        )
    }
}

/** Loads and changes terminal consent through the execution host's administration port. */
@Composable
internal fun TerminalConsentSettings(
    targetLabel: String,
    port: TerminalConsentPort,
) {
    var snapshot by remember(port) { mutableStateOf<TerminalConsentSnapshot?>(null) }
    var error by remember(port) { mutableStateOf<String?>(null) }
    var saving by remember(port) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(port) {
        try {
            snapshot = withContext(Dispatchers.IO) { port.snapshot() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.toUiErrorMessage()
        }
    }
    val current = snapshot
    if (current == null) {
        Text(error ?: "Loading terminal access…")
        return
    }
    TerminalConsentControl(
        targetLabel = targetLabel,
        executionNotice = current.executionNotice,
        grants = current.directories,
        enabled = current.enabled,
        canEnable = current.canEnable,
        saving = saving,
        error = error,
        onChange = { enabled ->
            if (!saving) {
                saving = true
                error = null
                scope.launch {
                    try {
                        snapshot = withContext(Dispatchers.IO) { port.setEnabled(enabled) }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        error = failure.toUiErrorMessage()
                        try {
                            snapshot = withContext(Dispatchers.IO) { port.snapshot() }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            snapshot = null
                        }
                    } finally {
                        saving = false
                    }
                }
            }
        },
    )
}

/** Presents terminal access consistently in Settings and onboarding. */
@Composable
internal fun TerminalAccessPage(port: TerminalConsentPort?) {
    Column {
        Text("Terminal Access", style = MaterialTheme.typography.headlineSmall)
        Text("Terminal access is optional. You can manage it later in Settings → Terminal Access.")
        if (port == null) {
            Text("Terminal access settings are unavailable for this connection.")
        } else {
            TerminalConsentSettings("Application server", port)
        }
    }
}
