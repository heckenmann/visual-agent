package de.heckenmann.visualagent.ui.directories

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.DirectoryAccessMode
import de.heckenmann.visualagent.protocol.DirectoryGrantOrigin

@Composable
internal fun DirectoryAccessModeSelector(
    selectedMode: DirectoryAccessMode,
    onModeChange: (DirectoryAccessMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DirectoryAccessMode.entries.forEach { mode ->
            val readOnly = mode == DirectoryAccessMode.READ_ONLY
            DirectoryRadioOption(
                selected = mode == selectedMode,
                label = if (readOnly) "Read only" else "Read and write",
                description =
                    if (readOnly) {
                        "Browse, search, and read files without making changes."
                    } else {
                        "Also create, edit, and delete files or folders in this directory."
                    },
            ) { onModeChange(mode) }
        }
    }
}

@Composable
internal fun DirectoryGrantOriginSelector(
    selectedOrigin: DirectoryGrantOrigin,
    onOriginChange: (DirectoryGrantOrigin) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DirectoryGrantOrigin.entries.forEach { origin ->
            val client = origin == DirectoryGrantOrigin.CLIENT
            DirectoryRadioOption(
                selected = origin == selectedOrigin,
                label = if (client) "This device" else "Application server",
                description =
                    if (client) {
                        "Choose a directory with this device's native file picker."
                    } else {
                        "Browse directories available to the configured application server."
                    },
            ) { onOriginChange(origin) }
        }
    }
}

@Composable
private fun DirectoryRadioOption(
    selected: Boolean,
    label: String,
    description: String,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(
                    shape,
                ).selectable(selected = selected, onClick = onClick)
                .pointerHoverIcon(PointerIcon.Hand),
        shape = shape,
        color = if (selected) colors.secondaryContainer.copy(alpha = 0.52f) else colors.surfaceVariant.copy(alpha = 0.18f),
        contentColor = if (selected) colors.onSecondaryContainer else colors.onSurface,
        border = BorderStroke(1.dp, if (selected) colors.primary else colors.outlineVariant.copy(alpha = 0.65f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RadioButton(selected = selected, onClick = null)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) colors.onSecondaryContainer.copy(alpha = 0.82f) else colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
