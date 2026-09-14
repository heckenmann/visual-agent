package de.heckenmann.visualagent.desktop

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import de.heckenmann.visualagent.ui.modal.ComposeConfirmationModal
import de.heckenmann.visualagent.ui.modal.ComposeContentModal
import de.heckenmann.visualagent.ui.modal.ComposeModalHost
import de.heckenmann.visualagent.ui.modal.ModalDialogLayout
import de.heckenmann.visualagent.ui.modal.modalPrimaryButton
import de.heckenmann.visualagent.ui.modal.modalSecondaryButton
import java.util.UUID

/** The transient dialog that edits or deletes one client-local server bookmark. */
internal sealed interface StartupServerBookmarkDialog {
    /** Opens an empty bookmark form. */
    data object Create : StartupServerBookmarkDialog

    /** Opens the bookmark form for an existing entry. */
    data class Edit(
        val bookmark: DesktopServerBookmark,
    ) : StartupServerBookmarkDialog

    /** Confirms deletion of an existing entry. */
    data class Delete(
        val bookmark: DesktopServerBookmark,
    ) : StartupServerBookmarkDialog
}

/** Renders the startup bookmark dialog above the complete splash surface. */
@Composable
internal fun ComposeStartupServerBookmarkDialog(
    dialog: StartupServerBookmarkDialog?,
    state: DesktopServerBookmarkState,
    onSave: (DesktopServerBookmarkState) -> Unit,
    onDismiss: () -> Unit,
    onDialogChange: (StartupServerBookmarkDialog?) -> Unit,
) {
    val modal =
        when (dialog) {
            null -> null
            StartupServerBookmarkDialog.Create -> bookmarkEditorModal(existing = null, onDismiss, onDialogChange, onSave, state)
            is StartupServerBookmarkDialog.Edit -> bookmarkEditorModal(dialog.bookmark, onDismiss, onDialogChange, onSave, state)
            is StartupServerBookmarkDialog.Delete ->
                ComposeConfirmationModal(
                    title = "Delete Visual Agent server",
                    message = "Delete the bookmark for ${dialog.bookmark.name}? This does not affect any Visual Agent server.",
                    confirmDescription = "Delete server",
                    onConfirm = {
                        val bookmarks = state.visualAgentServerBookmarks.filterNot { it.id == dialog.bookmark.id }
                        onSave(
                            state.copy(
                                visualAgentServerBookmarks = bookmarks,
                                lastSelectedVisualAgentServerId =
                                    state.lastSelectedVisualAgentServerId.takeIf { it != dialog.bookmark.id } ?: LOCAL_SERVER_ID,
                            ),
                        )
                    },
                )
        }
    ComposeModalHost(modal = modal, onDismiss = onDismiss)
}

private fun bookmarkEditorModal(
    existing: DesktopServerBookmark?,
    onDismiss: () -> Unit,
    onDialogChange: (StartupServerBookmarkDialog?) -> Unit,
    onSave: (DesktopServerBookmarkState) -> Unit,
    state: DesktopServerBookmarkState,
): ComposeContentModal =
    ComposeContentModal(
        title = if (existing == null) "Add Visual Agent server" else "Edit Visual Agent server",
        onDismiss = onDismiss,
    ) { dismiss ->
        ComposeStartupServerBookmarkEditor(
            existing = existing,
            state = state,
            onCancel = dismiss,
            onDelete = existing?.let { { onDialogChange(StartupServerBookmarkDialog.Delete(it)) } },
            onSave = { bookmark ->
                val bookmarks = state.visualAgentServerBookmarks.filterNot { it.id == bookmark.id } + bookmark
                val selected =
                    state.lastSelectedVisualAgentServerId.takeIf {
                        it == LOCAL_SERVER_ID || bookmarks.any { entry -> entry.id == it }
                    } ?: LOCAL_SERVER_ID
                onSave(state.copy(visualAgentServerBookmarks = bookmarks, lastSelectedVisualAgentServerId = selected))
                dismiss()
            },
        )
    }

/** Edits one remote Visual Agent application-server bookmark in the shared modal frame. */
@Composable
private fun ComposeStartupServerBookmarkEditor(
    existing: DesktopServerBookmark?,
    state: DesktopServerBookmarkState,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (DesktopServerBookmark) -> Unit,
) {
    var name by remember(existing) { mutableStateOf(existing?.name.orEmpty()) }
    var endpoint by remember(existing) { mutableStateOf(existing?.visualAgentServerEndpoint.orEmpty()) }
    val normalizedEndpoint =
        runCatching {
            DesktopServerEndpointSelector.format(DesktopServerEndpointSelector.parseRemoteEndpoint(endpoint))
        }.getOrNull()
    val error = bookmarkValidationError(name, endpoint, state, existing?.id)
    val unchanged = existing != null && name.trim() == existing.name && normalizedEndpoint == existing.visualAgentServerEndpoint
    ModalDialogLayout(
        body = {
            Text(
                "This bookmark connects the desktop client to a Visual Agent application server. It is not an LLM provider endpoint.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Server name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = endpoint,
                onValueChange = { endpoint = it },
                label = { Text("Visual Agent server endpoint") },
                supportingText = { Text("Secure endpoints use grpcs://host:port") },
                singleLine = true,
                isError = error != null,
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        },
        footer = {
            if (onDelete != null) modalSecondaryButton(label = "Delete server", icon = Icons.Filled.Delete, onClick = onDelete)
            modalSecondaryButton(label = "Cancel", onClick = onCancel)
            modalPrimaryButton(
                label = if (existing == null) "Save server" else "Save changes",
                icon = Icons.Filled.Save,
                enabled = error == null && !unchanged,
                onClick = {
                    onSave(
                        DesktopServerBookmark(
                            id = existing?.id ?: UUID.randomUUID().toString(),
                            name = name.trim(),
                            visualAgentServerEndpoint = endpoint.trim(),
                        ),
                    )
                },
            )
        },
    )
}

private fun bookmarkValidationError(
    name: String,
    endpoint: String,
    state: DesktopServerBookmarkState,
    editedBookmarkId: String?,
): String? =
    when {
        name.isBlank() -> "A server name is required."
        endpoint.isBlank() -> "A Visual Agent server endpoint is required."
        else ->
            runCatching { DesktopServerEndpointSelector.parseRemoteEndpoint(endpoint) }
                .fold(
                    onSuccess = { parsed ->
                        val normalized = DesktopServerEndpointSelector.format(parsed)
                        if (state.visualAgentServerBookmarks.any {
                                it.id != editedBookmarkId && it.visualAgentServerEndpoint == normalized
                            }
                        ) {
                            "A bookmark already uses this Visual Agent server endpoint."
                        } else {
                            null
                        }
                    },
                    onFailure = { failure -> failure.message },
                )
    }
