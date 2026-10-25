package de.heckenmann.visualagent.ui.conversation.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.model.ToolLiveState
import de.heckenmann.visualagent.ui.components.ActionIconButton
import kotlinx.coroutines.delay

/** Renders semantic snapshots using the active theme, without tool-name or network dispatch. */
@Composable
internal fun ToolLivePanel(
    state: ToolLiveState,
    onCancel: (() -> Unit)? = null,
) {
    var now by remember(state.executionId) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.executionId, state.deadlineEpochMillis, state.terminal) {
        val deadline = state.deadlineEpochMillis
        while (!state.terminal && deadline != null && now < deadline) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }
    Column(Modifier.fillMaxWidth().testTag("tool-live-panel"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (!state.terminal && onCancel != null) {
            ActionIconButton(icon = Icons.Filled.Stop, description = "Cancel tool execution", onClick = onCancel)
        }
        ToolLiveTextSection(state)
        state.deadlineEpochMillis?.takeUnless { state.terminal }?.let {
            LiveAnimatedText("${remainingToolSeconds(it, now)}s remaining", state.executionId, style = MaterialTheme.typography.labelSmall)
        }
        state.progress?.let { progress ->
            val total = progress.total
            if (total != null && total > 0) {
                LinearProgressIndicator(progress = {
                    (progress.current.toDouble() / total).coerceIn(0.0, 1.0).toFloat()
                }, modifier = Modifier.fillMaxWidth())
            }
            LiveAnimatedText(
                "${progress.current}${total?.let { " / $it" }.orEmpty()} ${progress.unit}",
                state.executionId,
                style = MaterialTheme.typography.labelSmall,
                terminal = state.terminal,
            )
            progress.perSecond?.takeIf { it > 0 && it.isFinite() }?.let { rate ->
                val remaining = total?.let { ((it - progress.current).coerceAtLeast(0) / rate).toLong() }
                LiveAnimatedText(
                    "${rate.toLong()} ${progress.unit}/s${remaining?.let { " · ${it}s remaining" }.orEmpty()}",
                    state.executionId,
                    style = MaterialTheme.typography.labelSmall,
                    terminal = state.terminal,
                )
            }
        }
        state.table?.let { table ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                table.columns.forEach {
                    LiveAnimatedText(
                        it,
                        state.executionId,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        terminal = state.terminal,
                        maxLines = Int.MAX_VALUE,
                        minLines = 1,
                    )
                }
            }
            table.rows.take(8).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach {
                        LiveAnimatedText(
                            it,
                            state.executionId,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            terminal = state.terminal,
                            maxLines = Int.MAX_VALUE,
                            minLines = 1,
                        )
                    }
                }
            }
        }
        ToolLiveArtifacts(state.artifacts, state.executionId, state.terminal)
        ToolLiveSeriesTable(state.series, state.executionId, state.terminal)
    }
}

/** Deadline-derived countdown, never an incrementally decremented presentation counter. */
internal fun remainingToolSeconds(
    deadlineEpochMillis: Long,
    nowEpochMillis: Long,
): Long = ((deadlineEpochMillis - nowEpochMillis).coerceAtLeast(0) + 999) / 1000
