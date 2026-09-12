package de.heckenmann.visualagent.ui.directories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.DirectoryGrantAdministrationPort
import de.heckenmann.visualagent.protocol.ServerDirectoryPickerEntry
import de.heckenmann.visualagent.ui.components.ActionIconButton
import de.heckenmann.visualagent.ui.components.PanelContentCard
import de.heckenmann.visualagent.ui.components.PanelInfoBox
import de.heckenmann.visualagent.ui.modal.modalPrimaryButton
import de.heckenmann.visualagent.ui.modal.modalSecondaryButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Presents a server-owned directory browser without exposing host paths to model tools. */
@Composable
internal fun ServerDirectoryPicker(
    directoryAccess: DirectoryGrantAdministrationPort,
    onSelect: (ServerDirectoryPickerEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf(emptyList<ServerDirectoryPickerEntry>()) }
    var current by remember { mutableStateOf<ServerDirectoryPickerEntry?>(null) }
    var history by remember { mutableStateOf(emptyList<ServerDirectoryPickerEntry?>()) }
    var filter by remember { mutableStateOf("") }
    var nextPageToken by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failure by remember { mutableStateOf<String?>(null) }
    val visibleEntries = entries.filter { it.displayName.contains(filter, ignoreCase = true) }

    /** Loads the roots or the child folders of a server selection. */
    suspend fun load(
        entry: ServerDirectoryPickerEntry?,
        pageToken: String? = null,
        append: Boolean = false,
    ) {
        loading = true
        runCatching {
            withContext(Dispatchers.IO) {
                entry?.let { directoryAccess.listServerDirectoryChildren(it.selectionId, pageToken) }
                    ?: directoryAccess.listServerDirectoryRoots(pageToken)
            }
        }.onSuccess {
            current = entry
            entries = if (append) entries + it.entries else it.entries
            nextPageToken = it.nextPageToken
            if (!append) filter = ""
            failure = null
        }.onFailure { failure = it.message ?: "Could not load server directories" }
        loading = false
    }

    /** Opens [entry] and retains the current directory as a local breadcrumb. */
    fun open(entry: ServerDirectoryPickerEntry) {
        scope.launch {
            history = history + current
            load(entry)
        }
    }
    LaunchedEffect(directoryAccess) { load(null) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PanelInfoBox("Choose a folder owned by the application server. This never grants access to this device.")
        ServerDirectoryLocationBar(
            current = current,
            canGoBack = history.isNotEmpty(),
            onGoBack = {
                scope.launch {
                    val parent = history.lastOrNull()
                    history = history.dropLast(1)
                    load(parent)
                }
            },
            onGoToRoots = {
                scope.launch {
                    history = emptyList()
                    load(null)
                }
            },
        )
        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            label = { Text("Filter folders") },
            leadingIcon = { androidx.compose.material3.Icon(Icons.Filled.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (loading) {
                item { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() } }
            } else if (visibleEntries.isEmpty()) {
                item {
                    PanelInfoBox(
                        if (entries.isEmpty()) "This folder has no child folders." else "No folders match the current filter.",
                    )
                }
            } else {
                items(visibleEntries, key = ServerDirectoryPickerEntry::selectionId) { entry ->
                    ServerDirectoryRow(entry, onOpen = { open(entry) })
                }
            }
        }
        failure?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        nextPageToken?.let { token ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                modalSecondaryButton("Load more folders", icon = Icons.Filled.ExpandMore) {
                    scope.launch { load(current, token, append = true) }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            modalSecondaryButton("Cancel", onClick = onDismiss)
            modalPrimaryButton("Choose this folder", enabled = current != null) {
                current?.let(onSelect)
            }
        }
    }
}

@Composable
private fun ServerDirectoryLocationBar(
    current: ServerDirectoryPickerEntry?,
    canGoBack: Boolean,
    onGoBack: () -> Unit,
    onGoToRoots: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ActionIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Go back", onGoBack, enabled = canGoBack)
        ActionIconButton(Icons.Filled.Home, "Show server roots", onGoToRoots, enabled = current != null)
        Column(modifier = Modifier.weight(1f)) {
            Text(if (current == null) "Server roots" else "Current folder", style = MaterialTheme.typography.labelLarge)
            Text(
                current?.location ?: "Choose a root folder to browse its contents.",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ServerDirectoryRow(
    entry: ServerDirectoryPickerEntry,
    onOpen: () -> Unit,
) {
    PanelContentCard(modifier = Modifier.clickable(onClick = onOpen)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(Icons.Filled.Folder, contentDescription = null)
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.displayName, style = MaterialTheme.typography.titleSmall)
                Text(entry.location, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            ActionIconButton(Icons.AutoMirrored.Filled.ArrowForward, "Open ${entry.displayName}", onOpen)
        }
    }
}
