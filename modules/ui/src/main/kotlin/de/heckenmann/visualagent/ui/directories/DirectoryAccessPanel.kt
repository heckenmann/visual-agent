package de.heckenmann.visualagent.ui.directories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.DirectoryAccessMode
import de.heckenmann.visualagent.protocol.DirectoryAccessPort
import de.heckenmann.visualagent.protocol.DirectoryGrantView
import de.heckenmann.visualagent.ui.components.ActionIconButton
import de.heckenmann.visualagent.ui.modal.ComposeConfirmationModal
import de.heckenmann.visualagent.ui.modal.ComposeModalRequester
import de.heckenmann.visualagent.ui.modal.requestConfirmation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Manages explicit server directory grants without performing filesystem I/O in Compose. */
@Composable
fun DirectoryAccessPanel(
    directoryAccess: DirectoryAccessPort,
    modalRequester: ComposeModalRequester,
) {
    val scope = rememberCoroutineScope()
    var grants by remember { mutableStateOf(emptyList<DirectoryGrantView>()) }
    var path by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(DirectoryAccessMode.READ_ONLY) }
    var inspected by remember { mutableStateOf<DirectoryGrantView?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    val refresh = {
        scope.launch {
            grants = withContext(Dispatchers.IO) { directoryAccess.listGrants() }
        }
        Unit
    }
    LaunchedEffect(directoryAccess) { refresh() }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Server directory", style = MaterialTheme.typography.titleSmall)
        Text(
            "Paths are resolved by the connected application server. Client directories require a connection-bound client capability and cannot fall back to this server flow.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(path, {
            path = it
            inspected = null
        }, label = { Text("Absolute server path") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(name, { name = it }, label = { Text("Display name") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(mode == DirectoryAccessMode.READ_ONLY, { mode = DirectoryAccessMode.READ_ONLY }, label = { Text("Read only") })
            FilterChip(
                mode == DirectoryAccessMode.READ_WRITE,
                { mode = DirectoryAccessMode.READ_WRITE },
                label = { Text("Read and write") },
            )
            ActionIconButton(Icons.Filled.Refresh, "Resolve server directory", onClick = {
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { directoryAccess.inspectServerDirectory(path) } }
                        .onSuccess {
                            inspected = it
                            if (name.isBlank()) name = it.displayName
                            status = "Resolved ${it.location}"
                        }.onFailure { status = it.message }
                }
            })
            ActionIconButton(Icons.Filled.Add, "Grant resolved server directory", enabled = inspected != null, onClick = {
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { directoryAccess.addServerGrant(path, name, mode) } }
                        .onSuccess {
                            path = ""
                            name = ""
                            inspected = null
                            status = "Directory granted"
                            refresh()
                        }.onFailure { status = it.message }
                }
            })
        }
        status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        grants.forEach { grant -> GrantRow(grant, directoryAccess, modalRequester, refresh) }
    }
}

@Composable
private fun GrantRow(
    grant: DirectoryGrantView,
    directoryAccess: DirectoryAccessPort,
    modalRequester: ComposeModalRequester,
    refresh: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by remember(grant.id, grant.displayName) { mutableStateOf(grant.displayName) }
    var mode by remember(grant.id, grant.mode) { mutableStateOf(grant.mode) }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            "${grant.origin.name.lowercase().replaceFirstChar(Char::uppercase)} · ${if (grant.available) "Available" else "Unavailable"}",
            style = MaterialTheme.typography.labelMedium,
        )
        grant.location?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        grant.diagnostic?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        OutlinedTextField(name, { name = it }, label = { Text("Display name") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(mode == DirectoryAccessMode.READ_ONLY, { mode = DirectoryAccessMode.READ_ONLY }, label = { Text("Read only") })
            FilterChip(
                mode == DirectoryAccessMode.READ_WRITE,
                { mode = DirectoryAccessMode.READ_WRITE },
                label = { Text("Read and write") },
            )
            ActionIconButton(Icons.Filled.Save, "Save directory grant", onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) { directoryAccess.updateGrant(grant.id, name, mode) }
                    refresh()
                }
            })
            ActionIconButton(Icons.Filled.Delete, "Remove directory grant", onClick = {
                modalRequester.requestConfirmation(
                    ComposeConfirmationModal(
                        "Remove directory access?",
                        "Revoke '${grant.displayName}' immediately.",
                        "Remove directory grant",
                    ) {
                        scope.launch {
                            withContext(Dispatchers.IO) { directoryAccess.removeGrant(grant.id) }
                            refresh()
                        }
                    },
                )
            })
        }
    }
}
