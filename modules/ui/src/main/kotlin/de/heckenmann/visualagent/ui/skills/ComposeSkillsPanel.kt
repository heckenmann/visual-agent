package de.heckenmann.visualagent.ui.skills

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
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
import com.mobilebytelabs.kmptoolkit.clipboard.copyToClipboard
import de.heckenmann.visualagent.protocol.ActivityPort
import de.heckenmann.visualagent.protocol.SkillCreateResult
import de.heckenmann.visualagent.protocol.SkillDeleteResult
import de.heckenmann.visualagent.protocol.SkillDocument
import de.heckenmann.visualagent.protocol.SkillPort
import de.heckenmann.visualagent.protocol.SkillSearchResult
import de.heckenmann.visualagent.protocol.SkillUpdateResult
import de.heckenmann.visualagent.ui.components.ActionIconButton
import de.heckenmann.visualagent.ui.components.ComposeMarkdown
import de.heckenmann.visualagent.ui.components.PanelContentCard
import de.heckenmann.visualagent.ui.components.PanelEmptyState
import de.heckenmann.visualagent.ui.components.PanelInfoBox
import de.heckenmann.visualagent.ui.components.RegisterPanelVerticalScrollbar
import de.heckenmann.visualagent.ui.modal.ComposeConfirmationModal
import de.heckenmann.visualagent.ui.modal.ComposeContentModal
import de.heckenmann.visualagent.ui.modal.ComposeModalRequester
import de.heckenmann.visualagent.ui.modal.modalDialogLayout
import de.heckenmann.visualagent.ui.modal.modalPrimaryButton
import de.heckenmann.visualagent.ui.modal.modalSaveButton
import de.heckenmann.visualagent.ui.modal.modalSecondaryButton
import de.heckenmann.visualagent.ui.modal.requestConfirmation
import de.heckenmann.visualagent.ui.status.ToolEventRefreshEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Presents the searchable, user-editable catalog of reusable model skills. */
@Composable
internal fun SkillsPanel(
    skillPort: SkillPort,
    activityPort: ActivityPort,
    modalRequester: ComposeModalRequester,
) {
    var query by remember { mutableStateOf("") }
    var skills by remember { mutableStateOf(emptyList<SkillSearchResult>()) }
    var selected by remember { mutableStateOf<SkillDocument?>(null) }
    var editing by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var conflict by remember { mutableStateOf<SkillConflict?>(null) }
    var status by remember { mutableStateOf("Loading skills…") }
    val scope = rememberCoroutineScope()
    lateinit var selectSkill: (String) -> Unit

    /** Reloads catalog metadata without blocking Compose's main dispatcher. */
    fun refresh() {
        if (query.codePointCount(0, query.length) > MAX_QUERY_CODE_POINTS) {
            status = "Search query is limited to $MAX_QUERY_CODE_POINTS Unicode code points."
            return
        }
        val current = selected
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    skillPort.search(query) to current?.let { skillPort.get(it.summary.id) }
                }
            }.onSuccess { (catalog, remote) ->
                skills = catalog
                current?.let { currentDocument ->
                    val draftDirty =
                        editing &&
                            (title != currentDocument.summary.title || content != currentDocument.content)
                    when {
                        remote == null && draftDirty -> conflict = SkillConflict.Deleted
                        remote == null && !draftDirty -> {
                            selected = null
                            editing = false
                        }
                        remote != null && remote.summary.revision != currentDocument.summary.revision && draftDirty -> {
                            conflict = SkillConflict.Changed(remote.summary)
                        }
                        remote != null && !draftDirty -> {
                            selected = remote
                            title = remote.summary.title
                            content = remote.content
                        }
                        remote != null -> {
                            selected = currentDocument.copy(summary = remote.summary)
                        }
                    }
                }
                status = "${catalog.size} skill${if (catalog.size == 1) "" else "s"}"
            }.onFailure { status = "Unable to load skills: ${it.message.orEmpty()}" }
        }
    }

    /** Closes the current editor draft and clears conflict state. */
    fun closeEditor() {
        editing = false
        conflict = null
    }

    /** Reloads the selected skill after an optimistic-concurrency conflict. */
    fun reloadEditor() {
        val id = selected?.summary?.id ?: return
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { skillPort.get(id) } }
                .onSuccess { document ->
                    if (document == null) {
                        conflict = SkillConflict.Deleted
                        status = "Skill was deleted elsewhere."
                    } else {
                        selected = document
                        title = document.summary.title
                        content = document.content
                        conflict = null
                    }
                }.onFailure { status = "Unable to reload skill: ${it.message.orEmpty()}" }
        }
    }

    /** Persists the current draft and reopens the saved document in the detail modal. */
    fun saveSkill(dismiss: () -> Unit) {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    if (selected == null) {
                        skillPort.create(title, content)
                    } else {
                        skillPort.update(
                            selected!!.summary.id,
                            selected!!.summary.revision,
                            title,
                            content,
                        )
                    }
                }
            }.onSuccess { result ->
                var savedId: String? = null
                when (result) {
                    is SkillCreateResult.Created -> savedId = result.skill.id
                    is SkillCreateResult.Duplicate -> status = "An equivalent skill already exists."
                    is SkillUpdateResult.Updated -> savedId = result.skill.id
                    is SkillUpdateResult.Conflict -> {
                        conflict = SkillConflict.Changed(result.skill)
                        status = "Skill changed elsewhere; reload it or keep your draft."
                    }
                    is SkillUpdateResult.Duplicate -> status = "An equivalent skill already exists."
                    SkillUpdateResult.NotFound -> {
                        conflict = SkillConflict.Deleted
                        status = "Skill was deleted elsewhere; save the draft as a new skill or discard it."
                    }
                }
                if (savedId != null) {
                    closeEditor()
                    dismiss()
                    refresh()
                    selectSkill(savedId)
                }
            }.onFailure { status = "Unable to save skill: ${it.message.orEmpty()}" }
        }
    }

    /** Opens the create or edit modal for the current selection. */
    fun openEditor() {
        editing = true
        modalRequester.request(
            ComposeContentModal(
                title = if (selected == null) "Create skill" else "Edit skill",
                onDismiss = ::closeEditor,
            ) { dismiss ->
                SkillEditor(
                    title = title,
                    content = content,
                    isNew = selected == null,
                    conflict = conflict,
                    onTitleChanged = { title = it },
                    onContentChanged = { content = it },
                    onCancel = {
                        closeEditor()
                        dismiss()
                    },
                    onReset = {
                        title = selected?.summary?.title.orEmpty()
                        content = selected?.content.orEmpty()
                    },
                    onSave = { saveSkill(dismiss) },
                    onReload = ::reloadEditor,
                    onKeepDraft = {
                        val changed = conflict as? SkillConflict.Changed
                        if (changed != null) selected = selected?.copy(summary = changed.skill)
                        conflict = null
                    },
                    onSaveAsNew = {
                        selected = null
                        conflict = null
                    },
                    onDiscard = {
                        selected = null
                        title = ""
                        content = ""
                        closeEditor()
                        dismiss()
                    },
                )
            },
        )
    }

    /** Opens the read-only detail modal for a complete skill document. */
    fun openDetail(document: SkillDocument) {
        editing = false
        modalRequester.request(
            ComposeContentModal(title = document.summary.title) { dismiss ->
                SkillDetail(
                    document = selected ?: document,
                    onEdit = ::openEditor,
                    onCopy = { copyToClipboard((selected ?: document).content) },
                    onDelete = {
                        val current = selected ?: document
                        modalRequester.requestConfirmation(
                            ComposeConfirmationModal(
                                title = "Delete skill",
                                message = "Delete ‘${current.summary.title}’? This cannot be undone.",
                                confirmDescription = "Delete",
                                onConfirm = {
                                    scope.launch {
                                        val result =
                                            withContext(Dispatchers.IO) {
                                                skillPort.delete(current.summary.id, current.summary.revision)
                                            }
                                        when (result) {
                                            is SkillDeleteResult.Deleted -> {
                                                selected = null
                                                refresh()
                                            }
                                            is SkillDeleteResult.Conflict ->
                                                status = "Skill changed elsewhere; reopen it before deleting."
                                            SkillDeleteResult.NotFound -> status = "Skill no longer exists."
                                        }
                                    }
                                },
                            ),
                        )
                    },
                    onDismiss = dismiss,
                )
            },
        )
    }

    /** Loads one complete skill and opens it in the shared modal. */
    selectSkill = { id ->
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { skillPort.get(id) } }
                .onSuccess { document ->
                    if (document == null) {
                        selected = null
                        status = "Skill no longer exists."
                    } else {
                        selected = document
                        title = document.summary.title
                        content = document.content
                        conflict = null
                        openDetail(document)
                    }
                }.onFailure { status = "Unable to open skill: ${it.message.orEmpty()}" }
        }
    }

    LaunchedEffect(skillPort) { refresh() }
    LaunchedEffect(query, skillPort) {
        delay(250)
        refresh()
    }
    ToolEventRefreshEffect(activityPort, setOf("skills"), onRefresh = ::refresh)

    val listScrollState = rememberScrollState()
    RegisterPanelVerticalScrollbar(listScrollState)
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search skills") },
                singleLine = true,
                isError = query.codePointCount(0, query.length) > MAX_QUERY_CODE_POINTS,
                supportingText = {
                    Text("${query.codePointCount(0, query.length)}/$MAX_QUERY_CODE_POINTS")
                },
                modifier = Modifier.weight(1f),
            )
            ActionIconButton(Icons.Filled.Search, "Search skills", ::refresh)
            ActionIconButton(Icons.Filled.Refresh, "Refresh skills", ::refresh)
            ActionIconButton(
                Icons.Filled.Add,
                "Create skill",
                onClick = {
                    selected = null
                    title = ""
                    content = ""
                    conflict = null
                    openEditor()
                },
            )
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(listScrollState),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (skills.isEmpty()) {
                PanelEmptyState(
                    "No skills found",
                    "Save stable, reusable Markdown results here for later model work, then open one to view or edit it.",
                )
            } else {
                skills.forEach { skill ->
                    PanelContentCard(
                        modifier = Modifier.clickable { selectSkill(skill.id) },
                        backgroundColor =
                            if (selected?.summary?.id == skill.id) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
                            } else {
                                null
                            },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(skill.title, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Revision ${skill.revision} · ${skill.readCount} model reads · Updated ${skill.updatedAt}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    skill.snippet.ifBlank { "No matching excerpt" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                )
                            }
                        }
                    }
                }
            }
        }
        Text(status, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SkillEditor(
    title: String,
    content: String,
    isNew: Boolean,
    conflict: SkillConflict?,
    onTitleChanged: (String) -> Unit,
    onContentChanged: (String) -> Unit,
    onCancel: () -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit,
    onReload: () -> Unit,
    onKeepDraft: () -> Unit,
    onSaveAsNew: () -> Unit,
    onDiscard: () -> Unit,
) {
    val titleLength = title.codePointCount(0, title.length)
    val contentLength = content.codePointCount(0, content.length)
    val titleInvalid = title.isBlank() || titleLength > MAX_TITLE_CODE_POINTS
    val contentInvalid = content.isBlank() || contentLength > MAX_CONTENT_CODE_POINTS
    modalDialogLayout(
        body = {
            conflict?.let { state ->
                PanelInfoBox(
                    when (state) {
                        is SkillConflict.Changed ->
                            "This skill changed elsewhere (revision ${state.skill.revision}). " +
                                "Reload the stored version or keep this draft."
                        SkillConflict.Deleted -> "This skill was deleted elsewhere. Save this draft as a new skill or discard it."
                    },
                )
            }
            OutlinedTextField(
                value = title,
                onValueChange = onTitleChanged,
                label = { Text("Title") },
                singleLine = true,
                isError = titleInvalid,
                supportingText = { Text("$titleLength/$MAX_TITLE_CODE_POINTS Unicode code points") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = content,
                onValueChange = onContentChanged,
                label = { Text("Markdown") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 12,
                isError = contentInvalid,
                supportingText = { Text("$contentLength/$MAX_CONTENT_CODE_POINTS Unicode code points") },
            )
        },
        footer = {
            when (val state = conflict) {
                is SkillConflict.Changed -> {
                    modalSecondaryButton("Reload stored version", icon = Icons.Filled.Restore, onClick = onReload)
                    modalSecondaryButton("Keep draft", icon = Icons.Filled.Edit, onClick = onKeepDraft)
                }
                SkillConflict.Deleted -> {
                    modalSecondaryButton("Save as new", icon = Icons.Filled.Save, onClick = onSaveAsNew)
                    modalSecondaryButton("Discard draft", icon = Icons.Filled.Close, onClick = onDiscard)
                }
                null -> Unit
            }
            modalSecondaryButton("Reset", icon = Icons.Filled.Restore, onClick = onReset)
            modalSecondaryButton("Cancel", onClick = onCancel)
            modalSaveButton(
                label = if (isNew) "Create skill" else "Save skill",
                enabled = !titleInvalid && !contentInvalid,
                onClick = onSave,
            )
        },
    )
}

private const val MAX_TITLE_CODE_POINTS = 200
private const val MAX_CONTENT_CODE_POINTS = 120_000
private const val MAX_QUERY_CODE_POINTS = 500

private sealed interface SkillConflict {
    data class Changed(
        val skill: SkillSearchResult,
    ) : SkillConflict

    data object Deleted : SkillConflict
}

@Composable
private fun SkillDetail(
    document: SkillDocument,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    modalDialogLayout(
        body = {
            Text(
                "Revision ${document.summary.revision} · ${document.summary.readCount} model reads" +
                    (document.summary.lastReadAt?.let { " · last read $it" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SelectionContainer {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ComposeMarkdown(document.content)
                }
            }
        },
        footer = {
            modalSecondaryButton("Copy Markdown", icon = Icons.Filled.ContentCopy, onClick = onCopy)
            modalSecondaryButton("Edit skill", icon = Icons.Filled.Edit, onClick = onEdit)
            modalSecondaryButton("Delete skill", icon = Icons.Filled.Delete, onClick = onDelete)
            modalPrimaryButton("Close", icon = Icons.Filled.Close, onClick = onDismiss)
        },
    )
}
