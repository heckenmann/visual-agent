package de.heckenmann.visualagent.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.ui.workspace.LocalSemanticActionColors

/** Renders the shared green save action with a checkmark icon. */
@Composable
internal fun saveActionButton(
    label: String = "Save changes",
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = LocalSemanticActionColors.current
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = colors.saveContainer, contentColor = colors.onSaveContainer),
    ) {
        Icon(Icons.Filled.Check, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

/** Renders the shared yellow reset action with a close icon. */
@Composable
internal fun resetActionButton(
    label: String = "Reset changes",
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = LocalSemanticActionColors.current
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = colors.resetContainer, contentColor = colors.onResetContainer),
    ) {
        Icon(Icons.Filled.Close, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}
