package de.heckenmann.visualagent.ui.settings.forms

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import de.heckenmann.visualagent.protocol.ProviderPort
import de.heckenmann.visualagent.protocol.ProviderProfile
import de.heckenmann.visualagent.protocol.SettingsPort
import de.heckenmann.visualagent.protocol.SettingsSection
import de.heckenmann.visualagent.ui.components.ActionIconButton
import de.heckenmann.visualagent.ui.components.PanelInfoBox
import de.heckenmann.visualagent.ui.components.PanelSection
import de.heckenmann.visualagent.ui.components.toUiErrorMessage
import de.heckenmann.visualagent.ui.settings.conversationSettingsSection
import de.heckenmann.visualagent.ui.settings.modelContextLengthSetting
import de.heckenmann.visualagent.ui.settings.modelContextLimitEffect
import de.heckenmann.visualagent.ui.settings.navigation.SettingsPageDraft
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Shows current-model context and message-composition preferences, never provider administration. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConversationSettingsPage(
    settings: SettingsPort,
    providers: ProviderPort,
    onSaved: () -> Unit,
    onManageProviders: () -> Unit,
) {
    var selected by remember { mutableStateOf(0) }
    val model = remember { SettingsPageDraft() }
    val conversation = remember { SettingsPageDraft() }
    var profiles by remember(providers) { mutableStateOf(emptyList<ProviderProfile>()) }
    var catalogError by remember(providers) { mutableStateOf<String?>(null) }
    var catalogRevision by remember(providers) { mutableStateOf(0) }
    LaunchedEffect(providers, catalogRevision) {
        try {
            profiles = withContext(Dispatchers.IO) { providers.listProviders() }
            catalogError = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            catalogError = error.toUiErrorMessage()
        }
    }
    Column(Modifier.fillMaxSize()) {
        catalogError?.let { error ->
            PanelInfoBox(error)
            ActionIconButton(Icons.Filled.Refresh, "Retry loading providers", onClick = { catalogRevision++ })
        }
        PrimaryTabRow(selectedTabIndex = selected) {
            listOf("Model", "Conversation").forEachIndexed { index, label ->
                Tab(selected == index, onClick = { selected = index }, text = { Text(label) })
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (selected == 0) {
                ScopedSettingsForm(settings, SettingsSection.MODEL, model, onSaved) { snapshot, change ->
                    val profile = profiles.firstOrNull { it.id == snapshot.providerId }
                    val selectedModel = profile?.models?.firstOrNull { it.id == snapshot.modelId }
                    var contextLimit by remember(snapshot.providerId, snapshot.modelId) { mutableStateOf(selectedModel?.contextLimit) }
                    modelContextLimitEffect(
                        loaded = true,
                        providerId = snapshot.providerId,
                        modelId = snapshot.modelId,
                        catalogLimit = selectedModel?.contextLimit,
                        selectionRevision = 0,
                        adoptLimit = false,
                        providerPort = providers,
                        onLimitResolved = { contextLimit = it },
                        onLimitApplied = { limit, _ ->
                            val current = model.draft.value
                            if (current.providerId == snapshot.providerId &&
                                current.modelId == snapshot.modelId &&
                                current.contextLength > limit
                            ) {
                                model.draft.value = current.copy(contextLength = limit)
                            }
                        },
                        onSelectionResolved = {},
                    )
                    PanelSection("Current model") {
                        Text(selectedModel?.name ?: snapshot.modelId)
                        Text(profile?.name ?: snapshot.providerId, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        modelContextLengthSetting(
                            snapshot.contextLength,
                            contextLimit,
                        ) { change(snapshot.copy(contextLength = it)) }
                        ActionIconButton(Icons.Filled.Settings, "Manage providers and model options", onClick = onManageProviders)
                    }
                }
            } else {
                ScopedSettingsForm(settings, SettingsSection.CONVERSATION, conversation, onSaved) { snapshot, change ->
                    conversationSettingsSection(snapshot, change, profiles)
                }
            }
        }
    }
}
