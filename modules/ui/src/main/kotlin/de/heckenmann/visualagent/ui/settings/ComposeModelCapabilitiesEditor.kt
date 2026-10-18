package de.heckenmann.visualagent.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.ProviderModel
import de.heckenmann.visualagent.protocol.capabilitySupport
import de.heckenmann.visualagent.ui.components.PanelDropdownField
import de.heckenmann.visualagent.ui.components.PanelSelectOption

/** Features that a user can explicitly declare for any discovered or manually added model. */
internal val EDITABLE_MODEL_CAPABILITIES =
    listOf("CHAT", "VISION", "AUDIO_INPUT", "AUDIO_OUTPUT", "IMAGE_GENERATION", "EMBEDDING", "TOOLS")

/** Displays independent automatic, supported and unsupported capability choices in the settings draft. */
@Composable
internal fun ModelCapabilitiesEditor(
    model: ProviderModel?,
    onChange: (String, Boolean?) -> Unit,
) {
    AnimatedVisibility(visible = model != null) {
        if (model != null) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Model capabilities", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Automatic uses provider evidence. Unknown is not the same as unsupported; each override affects only one capability.",
                    style = MaterialTheme.typography.bodySmall,
                )
                val automaticModel = model.copy(capabilityOverrides = emptyMap())
                val baselineKey = if (model.capabilitiesOverridden) "legacy" else "auto"
                val baselineLabel = if (model.capabilitiesOverridden) "Legacy declaration" else "Automatic"
                if (model.capabilitiesOverridden) {
                    Text(
                        "Reset the legacy manual declaration before choosing Automatic; no provider evidence is available for this declaration.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                EDITABLE_MODEL_CAPABILITIES.forEach { capability ->
                    PanelDropdownField(
                        label = capability.replace('_', ' '),
                        selectedValue =
                            model.capabilityOverrides[capability.lowercase()]?.toString()
                                ?: baselineKey,
                        options =
                            listOf(
                                PanelSelectOption(
                                    baselineKey,
                                    "$baselineLabel (${automaticModel.capabilitySupport(capability).name.lowercase()})",
                                ),
                                PanelSelectOption("true", "Supported"),
                                PanelSelectOption("false", "Unsupported"),
                            ),
                        onSelected = { onChange(capability, it.toBooleanStrictOrNull()) },
                    )
                }
            }
        }
    }
}
