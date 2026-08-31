package de.heckenmann.visualagent.ui.modal

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.ui.components.saveActionButton

/** Renders the standard secondary text action used in every modal title bar and footer. */
@Composable
internal fun modalSecondaryButton(
    label: String,
    icon: ImageVector = Icons.Filled.Close,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.semantics { contentDescription = label },
    ) {
        Icon(imageVector = icon, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

/** Renders the standard primary text action used in every modal footer. */
@Composable
internal fun modalPrimaryButton(
    label: String,
    icon: ImageVector = Icons.Filled.Check,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.semantics { contentDescription = label },
    ) {
        Icon(imageVector = icon, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

/** Renders the shared green save action in a modal footer. */
@Composable
internal fun modalSaveButton(
    label: String = "Save changes",
    enabled: Boolean = true,
    onClick: () -> Unit,
) = saveActionButton(label = label, enabled = enabled, onClick = onClick)
