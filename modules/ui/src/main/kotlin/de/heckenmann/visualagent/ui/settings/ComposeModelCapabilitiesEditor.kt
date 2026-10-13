package de.heckenmann.visualagent.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import de.heckenmann.visualagent.protocol.ProviderModel
import de.heckenmann.visualagent.ui.components.PanelCheckbox

/** Features that a user can explicitly declare for any discovered or manually added model. */
internal val EDITABLE_MODEL_CAPABILITIES =
    listOf("CHAT", "VISION", "AUDIO_INPUT", "AUDIO_OUTPUT", "IMAGE_GENERATION", "EMBEDDING", "TOOLS")

/** Displays model capabilities and persists manual toggles through the surrounding settings draft. */
@Composable
internal fun ModelCapabilitiesEditor(
    model: ProviderModel?,
    onChange: (Set<String>) -> Unit,
) {
    AnimatedVisibility(visible = model != null) {
        if (model != null) {
            Text("Model capabilities", style = MaterialTheme.typography.titleSmall)
            Text(
                "Provider-reported capabilities are shown below. Change a toggle to override the declaration for this model.",
                style = MaterialTheme.typography.bodySmall,
            )
            val enabled = model.capabilities.map { it.uppercase().replace('-', '_') }.toSet()
            EDITABLE_MODEL_CAPABILITIES.forEach { capability ->
                PanelCheckbox(
                    label = capability.replace('_', ' '),
                    checked = capability in enabled || capability == "CHAT" && !model.capabilitiesComplete && enabled.isEmpty(),
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
