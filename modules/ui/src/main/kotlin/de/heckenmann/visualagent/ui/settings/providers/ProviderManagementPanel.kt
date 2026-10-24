package de.heckenmann.visualagent.ui.settings.providers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.ProviderPort
import de.heckenmann.visualagent.protocol.ProviderProfile
import de.heckenmann.visualagent.protocol.SettingsPort
import de.heckenmann.visualagent.ui.components.ActionIconButton
import de.heckenmann.visualagent.ui.components.PanelScrollbarHost
import de.heckenmann.visualagent.ui.components.RegisterPanelVerticalScrollbar
import de.heckenmann.visualagent.ui.components.settingsDraftActionRow
import de.heckenmann.visualagent.ui.components.toUiErrorMessage
import de.heckenmann.visualagent.ui.settings.DirectAuthorizationControls
import de.heckenmann.visualagent.ui.settings.ProviderProfileEditor
import de.heckenmann.visualagent.ui.settings.ProviderSettingsDraft
import de.heckenmann.visualagent.ui.settings.newProviderFormState
import de.heckenmann.visualagent.ui.settings.providerProfileActions
import de.heckenmann.visualagent.ui.settings.providerSettingsDraft
import de.heckenmann.visualagent.ui.settings.remove
import de.heckenmann.visualagent.ui.settings.toFormState
import de.heckenmann.visualagent.ui.settings.upsert
import de.heckenmann.visualagent.ui.settings.withDirectCredentialReference
import de.heckenmann.visualagent.ui.settings.withModels
import de.heckenmann.visualagent.ui.workspace.PanelStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Keeps profile drafts and selection intact when Settings navigation changes. */
internal class ProviderManagementState {
    var persisted by mutableStateOf(ProviderSettingsDraft())
    var draft by mutableStateOf(ProviderSettingsDraft())
    var loaded by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var status by mutableStateOf("Loading providers...")
    var editing by mutableStateOf<ProviderProfile?>(null)
    var creating by mutableStateOf(false)
    var generation = 0
    val modelOptions = mutableStateMapOf<String, String>()
    var optionsDirty by mutableStateOf(false)
}

/** Manages provider definitions independently of the main agent's live provider/model selection. */
@Composable
internal fun ProviderManagementPanel(
    settings: SettingsPort,
    port: ProviderPort,
    state: ProviderManagementState = remember(port) { ProviderManagementState() },
    onSaved: () -> Unit = {},
    onRunOnboarding: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val draft = state.draft
    val selected = draft.providers.firstOrNull { it.id == draft.providerId }
    val dirty = state.loaded && (draft.providers != state.persisted.providers || state.optionsDirty)

    /** Reads the catalog baseline independently of the inspected provider. */
    fun reload() {
        val generation = ++state.generation
        val inspected = state.draft.providerId
        state.busy = true
        scope.launch {
            try {
                val current = withContext(Dispatchers.IO) { providerSettingsDraft(settings.snapshot(), port.listProviders()) }
                if (generation != state.generation) return@launch
                state.persisted = current
                state.draft =
                    if (current.providers.any { it.id == inspected }) {
                        current.copy(providerId = inspected, modelId = state.draft.modelId)
                    } else {
                        current
                    }
                state.loaded = true
                state.modelOptions.clear()
                state.optionsDirty = false
                state.status = "Provider settings loaded"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                state.status = error.toUiErrorMessage()
            } finally {
                if (generation == state.generation) state.busy = false
            }
        }
    }
    LaunchedEffect(port) {
        if (!state.loaded || (state.draft.providers == state.persisted.providers && !state.optionsDirty)) reload()
    }
    DisposableEffect(port) {
        val listener =
            port.addChangeListener {
                scope.launch {
                    if (state.draft.providers == state.persisted.providers && !state.optionsDirty && !state.busy) reload()
                }
            }
        onDispose { listener.close() }
    }
    if (state.creating || state.editing != null) {
        ProviderProfileEditor(
            initial = state.editing?.toFormState() ?: newProviderFormState(),
            existing = state.editing,
            canDisable = state.editing?.id != state.persisted.providerId,
            onCancel = {
                state.creating = false
                state.editing = null
            },
            onSave = {
                state.draft = state.draft.upsert(it)
                state.creating = false
                state.editing = null
            },
        )
        return
    }
    Column(Modifier.fillMaxSize()) {
        Row {
            providerProfileActions(
                canAdd = state.loaded && !state.busy,
                canEdit = selected != null && !state.busy,
                canRemove = selected != null && selected.id != state.persisted.providerId && !state.busy,
                onAdd = { state.creating = true },
                onEdit = { state.editing = selected },
                onRemove = { selected?.let { state.draft = state.draft.remove(it.id) } },
            )
            ActionIconButton(Icons.Filled.Explore, "Run onboarding again", onClick = onRunOnboarding)
        }
        val scroll = rememberScrollState()
        PanelScrollbarHost(Modifier.weight(1f).fillMaxWidth()) {
            RegisterPanelVerticalScrollbar(scroll)
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val directory: @Composable () -> Unit = {
                    ProviderDirectory(
                        draft.providers,
                        draft.providerId,
                        compact = maxWidth < 650.dp,
                        enabled = !state.busy,
                        onSelected = { id ->
                            val profile = draft.providers.first { it.id == id }
                            state.draft =
                                draft.copy(
                                    providerId = id,
                                    modelId =
                                        profile.defaultModel.ifBlank {
                                            profile.models
                                                .firstOrNull()
                                                ?.id
                                                .orEmpty()
                                        },
                                )
                        },
                    )
                }
                val details: @Composable () -> Unit = {
                    Column(Modifier.verticalScroll(scroll).padding(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        selected?.let { profile ->
                            Text(profile.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                profile.adapter.name.replace('_', ' '),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            DirectAuthorizationControls(profile, port) { id, reference ->
                                state.draft = state.draft.withDirectCredentialReference(id, reference)
                                state.persisted = state.persisted.withDirectCredentialReference(id, reference)
                                if (reference != null && state.persisted.providers.none { it.id == id }) {
                                    scope.launch {
                                        try {
                                            val registered = withContext(Dispatchers.IO) { port.getProvider(id) }
                                            if (registered != null) {
                                                state.persisted = state.persisted.copy(providers = state.persisted.providers + registered)
                                            }
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (error: Exception) {
                                            state.status = error.toUiErrorMessage()
                                        }
                                    }
                                }
                            }
                            ProviderModelCatalog(
                                draft,
                                state.busy,
                                state = state,
                                onChange = { state.draft = it },
                                onRefresh = {
                                    val generation = ++state.generation
                                    state.busy = true
                                    scope.launch {
                                        try {
                                            val models = withContext(Dispatchers.IO) { port.discoverModels(profile) }
                                            if (generation == state.generation) {
                                                state.draft = state.draft.withModels(profile.id, models)
                                                state.status = "Loaded ${models.size} models"
                                            }
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (error: Exception) {
                                            if (generation ==
                                                state.generation
                                            ) {
                                                state.status = error.toUiErrorMessage()
                                            }
                                        } finally {
                                            if (generation == state.generation) state.busy = false
                                        }
                                    }
                                },
                            )
                        } ?: Text("Add a provider to configure its connection and model catalog.")
                    }
                }
                if (maxWidth >= 650.dp) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.width(180.dp)) { directory() }
                        Column(Modifier.weight(1f)) { details() }
                    }
                } else {
                    Column {
                        directory()
                        details()
                    }
                }
            }
        }
        settingsDraftActionRow(
            dirty,
            state.busy,
            canSave =
                state.modelOptions.values.all(
                    ::validModelOptions,
                ),
            onReset = ::reload,
            onSave = {
                val submitted = state.draft.providers
                val baseline = state.persisted.providers
                state.busy = true
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { port.replaceProfiles(submitted, baseline) }
                        state.persisted = state.persisted.copy(providers = submitted)
                        state.optionsDirty = false
                        state.status = "Saved provider settings"
                        onSaved()
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
