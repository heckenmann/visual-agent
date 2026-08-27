@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.conversation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import de.heckenmann.visualagent.protocol.ConversationResponseTelemetry

/** Displays compact, safe model-response telemetry when the provider supplied it. */
@Composable
internal fun ResponseTelemetryFooter(telemetry: ConversationResponseTelemetry?) {
    val label = telemetry?.summaryLabel() ?: return
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Assistant response telemetry: $label" },
    )
}

/** Formats only the available high-level telemetry values for the collapsed footer. */
internal fun ConversationResponseTelemetry.summaryLabel(): String? =
    listOfNotNull(totalMillis?.let(::formatResponseDuration), totalTokens?.let(::formatTokenCount))
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" · ")

/** Formats durations without exposing provider nanosecond units. */
internal fun formatResponseDuration(millis: Long): String =
    when {
        millis < 1_000L -> "$millis ms"
        millis < 60_000L -> "%.1f s".format(java.util.Locale.ROOT, millis / 1_000.0)
        else -> "${millis / 60_000} min ${(millis % 60_000) / 1_000} s"
    }

/** Formats token counts compactly for the conversation footer. */
internal fun formatTokenCount(tokens: Int): String =
    if (tokens < 1_000) "$tokens tokens" else "%.1fk tokens".format(java.util.Locale.ROOT, tokens / 1_000.0)
