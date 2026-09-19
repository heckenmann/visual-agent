package de.heckenmann.visualagent.ui.application

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import de.heckenmann.visualagent.protocol.SettingsSnapshot
import de.heckenmann.visualagent.protocol.UpdatePort
import de.heckenmann.visualagent.protocol.UpdateRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Runs the persisted automatic update check without coupling update I/O to the main shell. */
@Composable
internal fun ComposeAutomaticUpdateCheck(
    settings: SettingsSnapshot,
    settingsLoaded: Boolean,
    updates: UpdatePort,
    onStateChanged: (UpdatePresentationState) -> Unit,
) {
    LaunchedEffect(updates, settingsLoaded, settings.automaticUpdatesEnabled) {
        if (settingsLoaded && settings.automaticUpdatesEnabled) {
            val result =
                withContext(Dispatchers.IO) {
                    runCatching {
                        updates.check(
                            UpdateRequest(includePrerelease = settings.includePrereleaseUpdates),
                        )
                    }
                }
            result
                .onSuccess { status ->
                    onStateChanged(UpdatePresentationState(status, updateMessage(status)))
                }.onFailure {
                    onStateChanged(UpdatePresentationState(message = "Automatic update check failed"))
                }
        }
    }
}

private fun updateMessage(status: de.heckenmann.visualagent.protocol.UpdateStatus): String =
    when {
        status.error != null -> status.error ?: "Update check failed"
        status.updateAvailable -> "Update ${status.latestVersion} is available"
        else -> "Visual Agent is up to date"
    }
