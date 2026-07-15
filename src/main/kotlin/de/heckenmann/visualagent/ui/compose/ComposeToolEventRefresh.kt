@file:Suppress("FunctionName")
@file:OptIn(kotlinx.coroutines.FlowPreview::class)

package de.heckenmann.visualagent.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import de.heckenmann.visualagent.agent.tools.ToolCallEvent
import de.heckenmann.visualagent.agent.tools.ToolCallPhase
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce

/**
 * Composable that subscribes to [ToolEventBus] and calls [onRefresh] when a
 * tool call matching [toolIds] completes successfully.
 *
 * Multiple matching events within [debounceMs] are collapsed into a single
 * refresh call. Only FINISHED events with `result.success == true` trigger
 * a refresh (unless [requireSuccess] is false).
 *
 * @param toolEventBus Spring-managed tool event bus
 * @param toolIds Set of tool IDs that should trigger a refresh
 * @param requireSuccess When true (default), only successful tool calls trigger refresh
 * @param debounceMs Debounce window in milliseconds
 * @param onRefresh Callback invoked after a matching tool call completes
 */
@Composable
internal fun ToolEventRefreshEffect(
    toolEventBus: ToolEventBus,
    toolIds: Set<String>,
    requireSuccess: Boolean = true,
    debounceMs: Long = 150L,
    onRefresh: () -> Unit,
) {
    val events = remember { MutableSharedFlow<ToolCallEvent>(extraBufferCapacity = 64) }
    DisposableEffect(toolEventBus) {
        val handle =
            toolEventBus.addListener { event ->
                if (event.phase == ToolCallPhase.FINISHED &&
                    event.toolId in toolIds &&
                    (!requireSuccess || event.result.success)
                ) {
                    events.tryEmit(event)
                }
            }
        onDispose { handle.close() }
    }
    LaunchedEffect(Unit) {
        events
            .debounce(debounceMs)
            .collect { onRefresh() }
    }
}
