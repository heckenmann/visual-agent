package de.heckenmann.visualagent.ui.conversation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.ui.components.ActionIconButton

/** Places manual refinement next to Send in either composer placement. */
@Composable
internal fun ComposerActions(
    input: String,
    sending: Boolean,
    busy: Boolean,
    contextReduced: Boolean,
    onInterpolate: () -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    interpolating: Boolean = false,
) {
    Row {
        ActionIconButton(
            icon = Icons.Filled.AutoFixHigh,
            description = "Refine prompt",
            onClick = onInterpolate,
            enabled = input.isNotBlank() && !busy,
            tooltipDescription = if (interpolating) "Interpolating prompt…" else "Refine prompt",
            modifier =
                Modifier.size(40.dp).testTag("prompt-interpolation-action").semantics {
                    if (interpolating) {
                        liveRegion = LiveRegionMode.Polite
                        progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                        stateDescription = "Interpolating prompt"
                    }
                },
            iconModifier = promptInterpolationIconModifier(interpolating),
        )
        if (sending || busy) {
            ActionIconButton(
                icon = Icons.Filled.Stop,
                description = if (busy) "Cancel prompt interpolation" else "Cancel response",
                onClick = onCancel,
                modifier = Modifier.size(40.dp),
            )
        } else {
            ActionIconButton(
                icon = Icons.AutoMirrored.Filled.Send,
                description = if (contextReduced) "Send message; context was reduced" else "Send message",
                onClick = onSend,
                enabled = input.isNotBlank(),
                modifier = Modifier.size(40.dp),
                tint = if (contextReduced) MaterialTheme.colorScheme.error else null,
            )
        }
    }
}
