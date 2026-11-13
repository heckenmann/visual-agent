package de.heckenmann.visualagent.ui.info

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.OpenSourceLicenseDocument
import de.heckenmann.visualagent.protocol.OpenSourceLicensePort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lists bundled license documents for the About panel without opening an external app. */
@Composable
internal fun OpenSourceLicensesPanel(port: OpenSourceLicensePort) {
    var documents by remember(port) { mutableStateOf<List<OpenSourceLicenseDocument>?>(null) }
    var selectedPath by remember(port) { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var error by remember(port) { mutableStateOf<String?>(null) }

    LaunchedEffect(port) {
        runCatching { withContext(Dispatchers.IO) { port.documents() } }
            .onSuccess { loaded ->
                documents = loaded
                selectedPath = loaded.firstOrNull(::isProjectLicense)?.path ?: loaded.firstOrNull()?.path
            }.onFailure { error = "Bundled license documents could not be loaded." }
    }

    val loaded = documents
    when {
        error != null -> Text(error!!, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
        loaded == null -> Text("Loading license documents…", Modifier.padding(16.dp))
        else ->
            OpenSourceLicensesContent(
                documents = loaded,
                selectedPath = selectedPath,
                search = search,
                onSearchChange = { search = it },
                onSelect = { selectedPath = it },
            )
    }
}

/** Keeps the Visual Agent project license ahead of matching third-party documents. */
internal fun orderProjectLicenseFirst(documents: List<OpenSourceLicenseDocument>): List<OpenSourceLicenseDocument> =
    documents.sortedWith(
        compareBy<OpenSourceLicenseDocument> { if (isProjectLicense(it)) 0 else 1 }
            .thenBy { it.component }
            .thenBy { it.path },
    )

private fun isProjectLicense(document: OpenSourceLicenseDocument): Boolean =
    document.component == "Visual Agent" && document.path == "LICENSE"

@Composable
internal fun OpenSourceLicensesContent(
    documents: List<OpenSourceLicenseDocument>,
    selectedPath: String?,
    search: String,
    onSearchChange: (String) -> Unit,
    onSelect: (String) -> Unit,
) {
    val matchingDocuments =
        orderProjectLicenseFirst(
            documents.filter { document ->
                search.isBlank() ||
                    document.component.contains(search, ignoreCase = true) ||
                    document.path.contains(search, ignoreCase = true)
            },
        )
    val selected = matchingDocuments.firstOrNull { it.path == selectedPath } ?: matchingDocuments.firstOrNull()
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth(0.34f).fillMaxSize().padding(end = 12.dp)) {
            OutlinedTextField(
                value = search,
                onValueChange = onSearchChange,
                label = { Text("Filter licenses") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("license-filter"),
            )
            Text(
                "${matchingDocuments.size} documents",
                Modifier.padding(vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyColumn(Modifier.fillMaxSize()) {
                items(matchingDocuments, key = { it.path }) { document ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(document.path) }
                            .padding(vertical = 8.dp, horizontal = 6.dp)
                            .testTag("license-document-${document.path}"),
                    ) {
                        Text(
                            document.component,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(document.path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider()
                }
            }
        }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)) {
            if (selected == null) {
                Text("Select a document", Modifier.padding(16.dp))
            } else {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Text(
                        selected.component,
                        Modifier.testTag("license-document-heading"),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        selected.path,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    SelectionContainer {
                        Text(
                            selected.text,
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("license-document-text"),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}
