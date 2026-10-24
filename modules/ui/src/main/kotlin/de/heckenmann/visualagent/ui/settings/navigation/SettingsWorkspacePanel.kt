package de.heckenmann.visualagent.ui.settings.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.SettingsSection
import de.heckenmann.visualagent.ui.application.ComposePanelServices
import de.heckenmann.visualagent.ui.settings.agent.AgentSettingsPage
import de.heckenmann.visualagent.ui.settings.agent.MainAgentMemoryDraft
import de.heckenmann.visualagent.ui.settings.providers.ProviderManagementPanel
import de.heckenmann.visualagent.ui.settings.providers.ProviderManagementState
import de.heckenmann.visualagent.ui.settings.settingsPanel

/** Gives global configuration stable, discoverable destinations without another workspace panel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsWorkspacePanel(services: ComposePanelServices) {
    val navigation = services.settingsNavigation
    val pages = remember(services.settings) { SettingsDestination.entries.associateWith { SettingsPageDraft() } }
    val providers = remember(services.providers) { ProviderManagementState() }
    val memory = remember(services.mainAgentMemory) { MainAgentMemoryDraft() }
    Column(Modifier.fillMaxSize()) {
        PrimaryScrollableTabRow(
            selectedTabIndex = navigation.destination.ordinal,
            edgePadding = 0.dp,
        ) {
            SettingsDestination.entries.forEach { destination ->
                Tab(
                    selected = destination == navigation.destination,
                    onClick = { navigation.destination = destination },
                    text = { Text(destination.label) },
                )
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (navigation.destination) {
                SettingsDestination.PROVIDERS ->
                    ProviderManagementPanel(
                        services.settings,
                        services.providers,
                        providers,
                        onSaved = services.onSettingsChanged,
                        onRunOnboarding = services.onRunOnboarding,
                    )
                SettingsDestination.AGENT ->
                    AgentSettingsPage(
                        services.settings,
                        services.mainAgentMemory,
                        pages.getValue(SettingsDestination.AGENT),
                        memoryDraft = memory,
                        onSaved = services.onSettingsChanged,
                    )
                else ->
                    settingsPanel(
                        settingsPort = services.settings,
                        onSettingsChanged = services.onSettingsChanged,
                        updatePort = services.updates,
                        updateState = services.updateState.value,
                        onUpdateStateChanged = services.onUpdateStateChanged,
                        section =
                            if (navigation.destination ==
                                SettingsDestination.UPDATES
                            ) {
                                SettingsSection.UPDATES
                            } else {
                                SettingsSection.APPEARANCE
                            },
                        state = pages.getValue(navigation.destination),
                    )
            }
        }
    }
}
