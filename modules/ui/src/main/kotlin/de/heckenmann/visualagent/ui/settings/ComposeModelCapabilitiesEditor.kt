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
import de.heckenmann.visualagent.ui.components.PanelCheckbox

/** Features that a user can explicitly declare for any discovered or manually added model. */
internal val EDITABLE_MODEL_CAPABILITIES =
    listOf("CHAT", "VISION", "AUDIO_INPUT", "AUDIO_OUTPUT", "IMAGE_GENERATION", "EMBEDDING", "TOOLS")

/** Returns editable capabilities, preserving the legacy chat default on unknown models. */
internal fun ProviderModel.capabilitiesForEditing(): Set<String> {
    val known = capabilities.map { it.uppercase().replace('-', '_') }.toSet()
    return if (!capabilitiesComplete && known.isEmpty()) known + "CHAT" else known
}

/** Displays model capabilities and persists manual toggles through the surrounding settings draft. */
@Composable
internal fun ModelCapabilitiesEditor(
    model: ProviderModel?,
    onChange: (Set<String>) -> Unit,
) {
    AnimatedVisibility(visible = model != null) {
        if (model != null) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Model capabilities", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Provider-reported capabilities are shown below. Change a toggle to override the declaration for this model.",
                    style = MaterialTheme.typography.bodySmall,
                )
                val enabled = model.capabilitiesForEditing()
                EDITABLE_MODEL_CAPABILITIES.forEach { capability ->
                    PanelCheckbox(
                        label = capability.replace('_', ' '),
                        checked = capability in enabled,
                        onCheckedChange = { checked ->
                            onChange(
                                if (checked) enabled + capability else enabled - capability,
                            )
                        },
                    )
                }
            }
        }
    }
}
