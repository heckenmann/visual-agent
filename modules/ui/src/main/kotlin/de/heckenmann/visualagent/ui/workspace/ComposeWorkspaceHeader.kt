@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.ui.agents.*
import de.heckenmann.visualagent.ui.application.*
import de.heckenmann.visualagent.ui.canvas.*
import de.heckenmann.visualagent.ui.components.*
import de.heckenmann.visualagent.ui.conversation.*
import de.heckenmann.visualagent.ui.files.*
import de.heckenmann.visualagent.ui.modal.*
import de.heckenmann.visualagent.ui.settings.*
import de.heckenmann.visualagent.ui.status.*
import de.heckenmann.visualagent.ui.todo.*
import de.heckenmann.visualagent.ui.workspace.*

@Composable
internal fun ComposeWorkspaceHeader(
    providerName: String,
    modelName: String,
    beanDefinitionCount: Int,
    inFlight: InFlightState,
    onStopAll: () -> Unit = {},
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Visual Agent",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Compose Multiplatform workspace",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        HeaderChip("Provider", providerName)
        HeaderChip("Model", modelName)
        HeaderChip("Beans", beanDefinitionCount.toString())
        InFlightIndicator(state = inFlight, onStopAll = onStopAll)
    }
}

@Composable
internal fun PanelStatus(status: String) {
    Text(status, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall)
}

@Composable
internal fun PanelWidthSlider(
    current: Int,
    onWidthChange: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var sliderValue by remember(current) { mutableFloatStateOf(current.toFloat()) }
    modalDialogLayout(
        body = {
            Text(
                text = "${sliderValue.toInt()} px",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
            )
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = { onWidthChange(sliderValue.toInt().coerceIn(MIN_PANEL_WIDTH, MAX_PANEL_WIDTH)) },
                valueRange = MIN_PANEL_WIDTH.toFloat()..MAX_PANEL_WIDTH.toFloat(),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        footer = {
            modalSecondaryButton(label = "Cancel", onClick = onDismiss)
            modalPrimaryButton(
                label = "Apply width",
                onClick = {
                    onWidthChange(sliderValue.toInt().coerceIn(MIN_PANEL_WIDTH, MAX_PANEL_WIDTH))
                    onDismiss()
                },
            )
        },
    )
}
