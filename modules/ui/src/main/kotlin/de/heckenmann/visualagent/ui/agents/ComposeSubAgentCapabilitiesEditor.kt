package de.heckenmann.visualagent.ui.agents

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import de.heckenmann.visualagent.protocol.CapabilitySupport
import de.heckenmann.visualagent.protocol.ProviderProfile
import de.heckenmann.visualagent.protocol.capabilitySupport
import de.heckenmann.visualagent.ui.components.PanelCheckbox
import de.heckenmann.visualagent.ui.components.PanelDropdownField
import de.heckenmann.visualagent.ui.components.PanelSelectOption
import de.heckenmann.visualagent.ui.settings.selectableModels

/** Specialized capabilities that may be enabled independently of the mandatory CHAT capability. */
internal val SPECIALIZED_MODEL_CAPABILITIES =
    listOf("VISION", "AUDIO_INPUT", "AUDIO_OUTPUT", "IMAGE_GENERATION", "EMBEDDING", "TOOLS")

/** Displays capability gates and provider-qualified, capability-filtered model assignments. */
@Composable
internal fun SubAgentCapabilitiesEditor(
    enabledCapabilities: Set<String>,
    capabilityModels: Map<String, String>,
    providers: List<ProviderProfile>,
    onEnabledChanged: (Set<String>) -> Unit,
    onModelsChanged: (Map<String, String>) -> Unit,
    onTest: (String, String) -> Unit,
) {
    Text("Capabilities", style = MaterialTheme.typography.titleSmall)
    Text(
        "CHAT is always enabled. Specialized capabilities require a compatible provider model.",
        style = MaterialTheme.typography.bodySmall,
    )
    SPECIALIZED_MODEL_CAPABILITIES.forEach { capability ->
        val enabled = capability in enabledCapabilities
        val eligible =
            providers
                .filter { it.enabled }
                .flatMap { profile ->
                    profile
                        .selectableModels()
                        .filter { it.capabilitySupport(capability) == CapabilitySupport.SUPPORTED }
                        .map { model ->
                            PanelSelectOption("${profile.id}::${model.id}", "${model.name} (${profile.name} — $capability)")
                        }
                }
        Column(modifier = Modifier.fillMaxWidth()) {
            PanelCheckbox(
                label = capability.replace('_', ' '),
                checked = enabled,
                onCheckedChange = { selected ->
                    onEnabledChanged(
                        if (selected) enabledCapabilities + capability else enabledCapabilities - capability,
                    )
                    if (!selected) onModelsChanged(capabilityModels - capability)
                },
            )
            AnimatedVisibility(visible = enabled) {
                Column {
                    PanelDropdownField(
                        label = "$capability model",
                        selectedValue = capabilityModels[capability].orEmpty(),
                        options = listOf(PanelSelectOption("", "Use default model")) + eligible,
                        onSelected = { model ->
                            onModelsChanged(
                                if (model.isBlank()) capabilityModels - capability else capabilityModels + (capability to model),
                            )
                        },
                        enabled = eligible.isNotEmpty() || capability in capabilityModels,
                        information = "Override the default with a catalog model declaring $capability.",
                    )
                    if (eligible.isEmpty()) {
                        Text("No $capability model is available. Enable it in the provider catalog first.")
                    }
                    val selected = capabilityModels[capability]
                    if (selected != null && eligible.any { it.value == selected }) {
                        OutlinedButton(onClick = { onTest(capability, selected) }) { Text("Test $capability model") }
                    }
                }
            }
        }
    }
}

/** Ensures each explicit override refers to an enabled capability and a selectable model. */
internal fun validCapabilityAssignments(
    enabled: Set<String>,
    selected: Map<String, String>,
    providers: List<ProviderProfile>,
): Boolean =
    "CHAT" in enabled &&
        selected.all { (capability, selection) ->
            if (capability !in enabled) return@all false
            if (capability == "CHAT") return@all true
            val providerId = selection.substringBefore("::")
            val modelId = selection.substringAfter("::", "")
            providers
                .firstOrNull { it.id == providerId && it.enabled }
                ?.selectableModels()
                ?.any { model ->
                    model.id == modelId &&
                        model.capabilitySupport(capability) == CapabilitySupport.SUPPORTED
                } == true
        }
