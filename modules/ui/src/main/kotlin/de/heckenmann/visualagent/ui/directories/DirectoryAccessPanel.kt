package de.heckenmann.visualagent.ui.directories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.ClientDirectoryGrantAdministrationPort
import de.heckenmann.visualagent.protocol.DirectoryAccessMode
import de.heckenmann.visualagent.protocol.DirectoryGrantAdministrationPort
import de.heckenmann.visualagent.protocol.DirectoryGrantOrigin
import de.heckenmann.visualagent.protocol.DirectoryGrantView
import de.heckenmann.visualagent.protocol.ServerDirectoryPickerEntry
import de.heckenmann.visualagent.ui.components.ActionIconButton
import de.heckenmann.visualagent.ui.components.PanelContentCard
import de.heckenmann.visualagent.ui.components.PanelInfoBox
import de.heckenmann.visualagent.ui.components.PanelSection
import de.heckenmann.visualagent.ui.modal.ComposeConfirmationModal
import de.heckenmann.visualagent.ui.modal.ComposeContentModal
import de.heckenmann.visualagent.ui.modal.ComposeModalRequester
import de.heckenmann.visualagent.ui.modal.modalPrimaryButton
import de.heckenmann.visualagent.ui.modal.modalSecondaryButton
import de.heckenmann.visualagent.ui.modal.requestConfirmation
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Manages explicit server directory grants without performing filesystem I/O in Compose. */
@Composable
fun DirectoryAccessPanel(
    directoryAccess: DirectoryGrantAdministrationPort,
    clientDirectoryAccess: ClientDirectoryGrantAdministrationPort,
    modalRequester: ComposeModalRequester,
) {
    val scope = rememberCoroutineScope()
    var grants by remember { mutableStateOf(emptyList<DirectoryGrantView>()) }
    var origin by remember { mutableStateOf(DirectoryGrantOrigin.SERVER) }
    var clientPath by remember { mutableStateOf("") }
    var serverSelection by remember { mutableStateOf<ServerDirectoryPickerEntry?>(null) }
    var name by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(DirectoryAccessMode.READ_ONLY) }
    var status by remember { mutableStateOf<String?>(null) }
    val directoryPicker =
        rememberDirectoryPickerLauncher { selected: PlatformFile? ->
            selected?.file?.let {
                clientPath = it.absolutePath
                if (name.isBlank()) name = it.name
                status = "Selected ${it.name}"
            }
        }
    val refresh = {
        scope.launch {
            grants = withContext(Dispatchers.IO) { directoryAccess.listGrants() }
        }
        Unit
    }
    LaunchedEffect(directoryAccess) { refresh() }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PanelSection("Grant directory access") {
            PanelInfoBox(
                "Choose where the directory is located. The filesystem owner validates every later operation.",
            )
            Text("Directory location", style = MaterialTheme.typography.labelLarge)
            DirectoryGrantOriginSelector(origin, onOriginChange = { origin = it })
            if (origin == DirectoryGrantOrigin.SERVER) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = serverSelection?.location.orEmpty(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Application server directory") },
                        modifier = Modifier.weight(1f),
                    )
                    ActionIconButton(
                        Icons.Filled.FolderOpen,
                        "Browse application server directories",
                        onClick = {
                            modalRequester.request(
                                ComposeContentModal("Choose application server directory") { dismiss ->
                                    ServerDirectoryPicker(
                                        directoryAccess = directoryAccess,
                                        onSelect = {
                                            serverSelection = it
                                            if (name.isBlank()) name = it.displayName
                                            dismiss()
                                        },
                                        onDismiss = dismiss,
                                    )
                                },
                            )
                        },
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = clientPath,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("This device directory") },
                        modifier = Modifier.weight(1f),
                    )
                    ActionIconButton(
                        Icons.Filled.FolderOpen,
                        "Choose this device directory",
                        onClick = { directoryPicker.launch() },
                    )
                }
            }
            OutlinedTextField(name, { name = it }, label = { Text("Display name") }, modifier = Modifier.fillMaxWidth())
            Text("Access mode", style = MaterialTheme.typography.labelLarge)
            DirectoryAccessModeSelector(mode, onModeChange = { mode = it })
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                modalPrimaryButton(
                    "Grant directory",
                    icon = Icons.Filled.Add,
                    enabled =
                        (origin == DirectoryGrantOrigin.SERVER && serverSelection != null) ||
                            (origin == DirectoryGrantOrigin.CLIENT && clientPath.isNotBlank()),
                ) {
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                when (origin) {
                                    DirectoryGrantOrigin.SERVER -> {
                                        val selection = requireNotNull(serverSelection)
                                        directoryAccess.addServerGrant(selection.selectionId, name, mode)
                                    }
                                    DirectoryGrantOrigin.CLIENT -> {
                                        directoryAccess.addClientGrant(
                                            clientDirectoryAccess.prepareDirectoryGrant(clientPath, mode),
                                            name,
                                            mode,
                                        )
                                    }
                                }
                            }
                        }.onSuccess {
                            serverSelection = null
                            clientPath = ""
                            name = ""
                            status = "Directory granted"
                            refresh()
                        }.onFailure { status = it.message }
                    }
                }
            }
        }
        status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        PanelSection("Granted directories (${grants.size})") {
            if (grants.isEmpty()) {
                PanelInfoBox("No additional directories have been granted to the application server.")
            } else {
                grants.forEach { grant -> GrantRow(grant, directoryAccess, clientDirectoryAccess, modalRequester, refresh) }
            }
        }
    }
}

@Composable
private fun GrantRow(
    grant: DirectoryGrantView,
    directoryAccess: DirectoryGrantAdministrationPort,
    clientDirectoryAccess: ClientDirectoryGrantAdministrationPort,
    modalRequester: ComposeModalRequester,
    refresh: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by remember(grant.id, grant.displayName) { mutableStateOf(grant.displayName) }
    var mode by remember(grant.id, grant.mode) { mutableStateOf(grant.mode) }
    var operationError by remember(grant.id) { mutableStateOf<String?>(null) }
    val reactivationPicker =
        rememberDirectoryPickerLauncher { selected: PlatformFile? ->
            selected?.file?.let { directory ->
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            directoryAccess.reactivateClientGrant(
                                clientDirectoryAccess.reactivateDirectoryGrant(grant.id, directory.absolutePath, grant.mode),
                            )
                        }
                    }.onSuccess { refresh() }.onFailure { operationError = it.message ?: "Could not reconnect the directory" }
                }
            }
        }
    PanelContentCard {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(grant.displayName, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${grant.origin.name.lowercase().replaceFirstChar(Char::uppercase)} directory",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    grant.ownerLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                if (grant.available) "Available" else "Unavailable",
                style = MaterialTheme.typography.labelLarge,
                color = if (grant.available) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
        grant.location?.let {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Server location", style = MaterialTheme.typography.labelMedium)
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
        grant.diagnostic?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        operationError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        OutlinedTextField(name, { name = it }, label = { Text("Display name") }, modifier = Modifier.fillMaxWidth())
        Text("Access mode", style = MaterialTheme.typography.labelLarge)
        DirectoryAccessModeSelector(mode, onModeChange = { mode = it })
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            if (grant.origin == DirectoryGrantOrigin.CLIENT && !grant.available) {
                modalSecondaryButton("Reconnect directory", icon = Icons.Filled.FolderOpen) {
                    reactivationPicker.launch()
                }
            }
            modalSecondaryButton("Remove", icon = Icons.Filled.Delete) {
                modalRequester.requestConfirmation(
                    ComposeConfirmationModal(
                        "Remove directory access?",
                        "Revoke '${grant.displayName}' immediately.",
                        "Remove directory grant",
                    ) {
                        scope.launch {
                            withContext(Dispatchers.IO) { directoryAccess.removeGrant(grant.id) }
                            if (grant.origin == DirectoryGrantOrigin.CLIENT) clientDirectoryAccess.revokeDirectoryGrant(grant.id)
                            refresh()
                        }
                    },
                )
            }
            modalPrimaryButton("Save changes", icon = Icons.Filled.Save) {
                scope.launch {
                    withContext(Dispatchers.IO) { directoryAccess.updateGrant(grant.id, name, mode) }
                    refresh()
                }
            }
        }
    }
}
