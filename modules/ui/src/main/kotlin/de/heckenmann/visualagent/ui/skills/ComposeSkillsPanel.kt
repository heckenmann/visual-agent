package de.heckenmann.visualagent.ui.skills

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.mobilebytelabs.kmptoolkit.clipboard.copyToClipboard
import de.heckenmann.visualagent.protocol.ActivityPort
import de.heckenmann.visualagent.protocol.SkillCreateResult
import de.heckenmann.visualagent.protocol.SkillDeleteResult
import de.heckenmann.visualagent.protocol.SkillDocument
import de.heckenmann.visualagent.protocol.SkillPort
import de.heckenmann.visualagent.protocol.SkillSearchResult
import de.heckenmann.visualagent.protocol.SkillUpdateResult
import de.heckenmann.visualagent.ui.modal.ComposeConfirmationModal
import de.heckenmann.visualagent.ui.modal.ComposeContentModal
import de.heckenmann.visualagent.ui.modal.ComposeModalRequester
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
    var saveError by remember { mutableStateOf<String?>(null) }
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
        saveError = null
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
        saveError = null
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
                    is SkillCreateResult.Duplicate -> {
                        saveError = "An equivalent skill already exists: ${result.skill.title}."
                        status = "An equivalent skill already exists."
                    }
                    is SkillUpdateResult.Updated -> savedId = result.skill.id
                    is SkillUpdateResult.Conflict -> {
                        conflict = SkillConflict.Changed(result.skill)
                        status = "Skill changed elsewhere; reload it or keep your draft."
                    }
                    is SkillUpdateResult.Duplicate -> {
                        saveError = "An equivalent skill already exists: ${result.skill.title}."
                        status = "An equivalent skill already exists."
                    }
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
                    saveError = saveError,
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

    SkillsCatalogContent(
        query = query,
        skills = skills,
        selectedId = selected?.summary?.id,
        status = status,
        onQueryChanged = { query = it },
        onSearch = ::refresh,
        onCreate = {
            selected = null
            title = ""
            content = ""
            conflict = null
            saveError = null
            openEditor()
        },
        onSelect = selectSkill,
    )
}
