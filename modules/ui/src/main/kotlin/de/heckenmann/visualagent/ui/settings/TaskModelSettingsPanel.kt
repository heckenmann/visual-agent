package de.heckenmann.visualagent.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.CapabilitySupport
import de.heckenmann.visualagent.protocol.ProviderAdapter
import de.heckenmann.visualagent.protocol.ProviderPort
import de.heckenmann.visualagent.protocol.ProviderProfile
import de.heckenmann.visualagent.protocol.TaskModelAssignment
import de.heckenmann.visualagent.protocol.capabilitySupport
import de.heckenmann.visualagent.ui.components.PanelDropdownField
import de.heckenmann.visualagent.ui.components.PanelInfoBox
import de.heckenmann.visualagent.ui.components.PanelScrollbarHost
import de.heckenmann.visualagent.ui.components.PanelSection
import de.heckenmann.visualagent.ui.components.PanelSelectOption
import de.heckenmann.visualagent.ui.components.RegisterPanelVerticalScrollbar
import de.heckenmann.visualagent.ui.components.settingsDraftActionRow
import de.heckenmann.visualagent.ui.components.toUiErrorMessage
import de.heckenmann.visualagent.ui.workspace.PanelStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val taskModelCapabilities = listOf("VISION", "AUDIO_INPUT", "AUDIO_OUTPUT", "EMBEDDING")

/** Holds persisted and editable application-wide task model choices. */
internal class TaskModelSettingsState {
    var profiles by mutableStateOf<List<ProviderProfile>>(emptyList())
    var assignments by mutableStateOf<List<TaskModelAssignment>>(emptyList())
    var persistedAssignments by mutableStateOf<List<TaskModelAssignment>>(emptyList())
    var loaded by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var status by mutableStateOf("Loading task models...")
    var generation = 0
}

/** Configures one global model per task, independently from the provider profile editor. */
@Composable
internal fun TaskModelSettingsPanel(
    port: ProviderPort,
    state: TaskModelSettingsState = remember(port) { TaskModelSettingsState() },
) {
    val scope = rememberCoroutineScope()
    val dirty = state.loaded && state.assignments != state.persistedAssignments

    /** Reloads the persisted task assignments without blocking the UI. */
    fun reload() {
        val generation = ++state.generation
        state.busy = true
        scope.launch {
            try {
                val (profiles, assignments) =
                    withContext(Dispatchers.IO) {
                        port.listProviders() to port.taskModelAssignments().sortedBy(TaskModelAssignment::capability)
                    }
                if (generation != state.generation) return@launch
                state.profiles = profiles
                state.assignments = assignments
                state.persistedAssignments = assignments
                state.loaded = true
                state.status = "Task model assignments loaded"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                state.status = error.toUiErrorMessage()
            } finally {
                if (generation == state.generation) state.busy = false
            }
        }
    }

    LaunchedEffect(port) { if (!state.loaded) reload() }
    DisposableEffect(port) {
        val listener =
            port.addChangeListener {
                scope.launch {
                    if (state.assignments == state.persistedAssignments && !state.busy) reload()
                }
            }
        onDispose { listener.close() }
    }

    Column(Modifier.fillMaxSize()) {
        PanelScrollbarHost(Modifier.weight(1f).fillMaxWidth()) {
            val scroll = rememberScrollState()
            RegisterPanelVerticalScrollbar(scroll)
            Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PanelInfoBox(
                    "These choices apply globally. Visual Agent automatically assigns an available capable model when none is selected. " +
                        "An explicit sub-agent assignment takes precedence. Speech processing is not yet implemented.",
                )
                taskModelCapabilities.forEach { capability ->
                    TaskModelAssignmentField(
                        capability = capability,
                        profiles = state.profiles,
                        assignment = state.assignments.firstOrNull { it.capability == capability },
                        enabled = state.loaded && !state.busy,
                    ) { assignment ->
                        state.assignments =
                            (state.assignments.filterNot { it.capability == capability } + listOfNotNull(assignment))
                                .sortedBy(TaskModelAssignment::capability)
                    }
                }
            }
        }
        settingsDraftActionRow(
            hasUnsavedChanges = dirty,
            saving = state.busy,
            onReset = ::reload,
            onSave = {
                val submitted = state.assignments
                val baseline = state.persistedAssignments
                state.busy = true
                scope.launch {
                    try {
                        val (profiles, assignments) =
                            withContext(Dispatchers.IO) {
                                port.replaceTaskModelAssignments(submitted, baseline)
                                port.listProviders() to port.taskModelAssignments().sortedBy(TaskModelAssignment::capability)
                            }
                        state.profiles = profiles
                        state.assignments = assignments
                        state.persistedAssignments = assignments
                        state.status = "Saved task model assignments"
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        state.status = error.toUiErrorMessage()
                    } finally {
                        state.busy = false
                    }
                }
            },
        )
        PanelStatus(state.status)
    }
}

@Composable
private fun TaskModelAssignmentField(
    capability: String,
    profiles: List<ProviderProfile>,
    assignment: TaskModelAssignment?,
    enabled: Boolean,
    onChange: (TaskModelAssignment?) -> Unit,
) {
    val options =
        profiles
            .filter(ProviderProfile::enabled)
            .filter { capability != "EMBEDDING" || it.adapter == ProviderAdapter.OLLAMA }
            .flatMap { profile ->
                profile
                    .selectableModels()
                    .filter { it.capabilitySupport(capability.lowercase()) == CapabilitySupport.SUPPORTED }
                    .map { model ->
                        PanelSelectOption("${profile.id}::${model.id}", "${profile.name} / ${model.name}")
                    }
            }.sortedBy(PanelSelectOption::label)
    val selectedValue = assignment?.let { "${it.providerId}::${it.modelId}" }.orEmpty()
    val currentUnavailable = assignment != null && options.none { it.value == selectedValue }
    val visibleOptions =
        buildList {
            add(PanelSelectOption("", "Automatically assigned when available"))
            if (currentUnavailable) {
                add(PanelSelectOption(selectedValue, "Unavailable: ${assignment.providerId}/${assignment.modelId}"))
            }
            addAll(options)
        }
    PanelSection(title = capability.settingLabel()) {
        PanelDropdownField(
            label = "Model",
            selectedValue = selectedValue,
            options = visibleOptions,
            enabled = enabled,
            onSelected = { value ->
                if (value.isBlank()) {
                    onChange(null)
                    return@PanelDropdownField
                }
                if (currentUnavailable && value == selectedValue) return@PanelDropdownField
                val (providerId, modelId) = value.split("::", limit = 2)
                onChange(TaskModelAssignment(capability, providerId, modelId))
            },
        )
    }
}

private fun String.settingLabel(): String =
    when (this) {
        "VISION" -> "Vision"
        "AUDIO_INPUT" -> "Speech recognition"
        "AUDIO_OUTPUT" -> "Speech output"
        "EMBEDDING" -> "Embeddings"
        else -> this
    }
