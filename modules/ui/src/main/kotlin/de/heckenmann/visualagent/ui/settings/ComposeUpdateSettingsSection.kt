package de.heckenmann.visualagent.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.SettingsSnapshot
import de.heckenmann.visualagent.protocol.StagedUpdate
import de.heckenmann.visualagent.protocol.UpdatePort
import de.heckenmann.visualagent.protocol.UpdateRequest
import de.heckenmann.visualagent.protocol.UpdateStatus
import de.heckenmann.visualagent.ui.application.UpdatePresentationState
import de.heckenmann.visualagent.ui.components.ActionIconButton
import de.heckenmann.visualagent.ui.components.PanelCheckbox
import de.heckenmann.visualagent.ui.components.PanelInfoBox
import de.heckenmann.visualagent.ui.components.PanelSection
import de.heckenmann.visualagent.ui.components.toUiErrorMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Renders persisted update preferences and the explicit release actions. */
@Composable
internal fun UpdateSettingsSection(
    settings: SettingsSnapshot,
    updatePort: UpdatePort,
    initialState: UpdatePresentationState = UpdatePresentationState(),
    onStateChanged: (UpdatePresentationState) -> Unit = {},
    onChange: (SettingsSnapshot) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var status by remember(initialState) { mutableStateOf(initialState.status) }
    var message by remember(initialState) { mutableStateOf(initialState.message ?: "No update check performed") }
    var checking by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var staged by remember { mutableStateOf<StagedUpdate?>(null) }

    /** Runs a manual update check without blocking the Compose dispatcher. */
    fun check() {
        if (checking) return
        checking = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    updatePort.check(UpdateRequest(includePrerelease = settings.includePrereleaseUpdates))
                }
            }.onSuccess { result ->
                status = result
                message = updateMessage(result)
                onStateChanged(UpdatePresentationState(result, message))
            }.onFailure { error ->
                status = null
                message = error.toUiErrorMessage()
                onStateChanged(UpdatePresentationState(message = message))
            }
            checking = false
        }
    }

    PanelSection(title = "Updates") {
        PanelCheckbox(
            label = "Check for updates automatically",
            checked = settings.automaticUpdatesEnabled,
            onCheckedChange = { onChange(settings.copy(automaticUpdatesEnabled = it)) },
            information = "Checks GitHub releases when the application starts.",
        )
        PanelCheckbox(
            label = "Include preview releases",
            checked = settings.includePrereleaseUpdates,
            onCheckedChange = { onChange(settings.copy(includePrereleaseUpdates = it)) },
            information = "Includes prerelease versions in update checks.",
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(message, modifier = Modifier.weight(1f))
            ActionIconButton(
                icon = Icons.Filled.Refresh,
                description = "Check for updates",
                onClick = ::check,
                enabled = !checking,
            )
            if (status?.updateAvailable == true) {
                ActionIconButton(
                    icon = Icons.Filled.Download,
                    description = "Download available update",
                    onClick = {
                        downloading = true
                        scope.launch {
                            val result =
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        updatePort.download(
                                            UpdateRequest(
                                                includePrerelease = settings.includePrereleaseUpdates,
                                                releaseTag = status?.releaseTag,
                                                assetName = status?.selectedAsset?.name,
                                            ),
                                        )
                                    }
                                }
                            result
                                .onSuccess { download ->
                                    staged = download.staged
                                    message = download.error ?: "Update downloaded and verified"
                                }.onFailure { error -> message = error.toUiErrorMessage() }
                            downloading = false
                        }
                    },
                    enabled = !downloading,
                )
            }
            staged?.let { verified ->
                ActionIconButton(
                    icon = Icons.Filled.PlayArrow,
                    description = "Start verified update installer",
                    onClick = {
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { updatePort.install(verified.stagedId) }
                            message = result.message
                            if (result.started) staged = null
                        }
                    },
                )
            }
        }
        status?.let { checked ->
            PanelInfoBox(
                buildString {
                    appendLine("Current version: ${checked.currentVersion}")
                    checked.latestVersion?.let { appendLine("Available version: $it") }
                    checked.selectedAsset?.let {
                        appendLine("Package: ${it.name}")
                        appendLine("Size: ${it.sizeBytes} bytes")
                    }
                }.trimEnd(),
                maxLines = 5,
            )
            checked.releaseUrl?.let { url ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(url, modifier = Modifier.weight(1f), maxLines = 1)
                    ActionIconButton(
                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                        description = "Open release page",
                        onClick = { uriHandler.openUri(url) },
                    )
                }
            }
        }
        status?.releaseNotes?.takeIf(String::isNotBlank)?.let { notes -> PanelInfoBox(notes, maxLines = 6) }
    }
}

private fun updateMessage(status: UpdateStatus): String =
    when {
        status.error != null -> status.error ?: "Update check failed"
        status.updateAvailable -> "Update ${status.latestVersion} is available"
        else -> "Visual Agent is up to date"
    }
