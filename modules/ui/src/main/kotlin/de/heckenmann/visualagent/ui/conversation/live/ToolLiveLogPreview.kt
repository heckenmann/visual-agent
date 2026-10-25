package de.heckenmann.visualagent.ui.conversation.live

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import de.heckenmann.visualagent.model.ToolLiveState

/** Selects the tail by native visual lines, rather than hiding recent output behind older wrapped entries. */
@Composable
internal fun ToolLiveLogPreview(
    state: ToolLiveState,
    onOverflow: (Boolean) -> Unit,
) {
    val measurer = rememberTextMeasurer()
    val themeStyle = MaterialTheme.typography.bodySmall
    val style =
        remember(themeStyle) {
            themeStyle.copy(fontFeatureSettings = listOfNotNull(themeStyle.fontFeatureSettings, "tnum").joinToString(","))
        }
    BoxWithConstraints {
        val preview =
            remember(state.logs, style, measurer, constraints.maxWidth) {
                liveLogTail(state.logs, style, measurer, constraints.maxWidth)
            }
        LaunchedEffect(preview.second) { onOverflow(preview.second) }
        LiveAnimatedText(preview.first, state.executionId, themeStyle, maxLines = 6, minLines = 1, terminal = state.terminal)
    }
}

/** Measures at most six bounded entries and keeps their final six visual lines, including soft wraps. */
internal fun liveLogTail(
    logs: List<String>,
    style: TextStyle,
    measurer: TextMeasurer,
    maxWidth: Int,
): Pair<String, Boolean> {
    val text = logs.takeLast(6).joinToString("\n")
    val layout = measurer.measure(text, style, constraints = Constraints(maxWidth = maxWidth))
    val firstLine = (layout.lineCount - 6).coerceAtLeast(0)
    return text.substring(layout.getLineStart(firstLine)) to (logs.size > 6 || firstLine > 0)
}
