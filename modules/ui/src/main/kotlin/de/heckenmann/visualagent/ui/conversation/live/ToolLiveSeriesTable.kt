package de.heckenmann.visualagent.ui.conversation.live

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import de.heckenmann.visualagent.model.ToolLiveSeries

/** Accessible numeric-series presentation using Material text instead of a custom chart engine. */
@Composable
internal fun ToolLiveSeriesTable(
    series: List<ToolLiveSeries>,
    identity: String,
    terminal: Boolean,
) {
    series.forEach { measured ->
        Column {
            LiveAnimatedText(measured.label, identity, style = MaterialTheme.typography.labelMedium, terminal = terminal)
            val firstVisible = (measured.values.size - 8).coerceAtLeast(0)
            measured.values.takeLast(8).forEachIndexed { index, value ->
                Row {
                    Text("Sample ${firstVisible + index + 1}", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                    LiveAnimatedText(
                        "$value ${measured.unit}",
                        identity,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        terminal = terminal,
                    )
                }
            }
        }
    }
}
