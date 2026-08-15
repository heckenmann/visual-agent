@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")
@file:OptIn(kotlinx.coroutines.FlowPreview::class)

package de.heckenmann.visualagent.ui.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import de.heckenmann.visualagent.protocol.ActivityPort
import de.heckenmann.visualagent.protocol.ToolActivity
import de.heckenmann.visualagent.protocol.ToolActivityPhase
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce

/**
 * Composable that subscribes to [ActivityPort] and calls [onRefresh] when a
 * tool call matching [toolIds] completes successfully.
 *
 * Multiple matching events within [debounceMs] are collapsed into a single
 * refresh call. Only FINISHED events with `result.success == true` trigger
 * a refresh (unless [requireSuccess] is false).
 *
 * @param activityPort Transport-owned activity events
 * @param toolIds Set of tool IDs that should trigger a refresh
 * @param requireSuccess When true (default), only successful tool calls trigger refresh
 * @param debounceMs Debounce window in milliseconds
 * @param onRefresh Callback invoked after a matching tool call completes
 */
@Composable
internal fun ToolEventRefreshEffect(
    activityPort: ActivityPort,
    toolIds: Set<String>,
    requireSuccess: Boolean = true,
    debounceMs: Long = 150L,
    onRefresh: () -> Unit,
) {
    val events = remember { MutableSharedFlow<ToolActivity>(extraBufferCapacity = 64) }
    DisposableEffect(activityPort) {
        val handle =
            activityPort.addToolListener { event ->
                if (event.phase == ToolActivityPhase.FINISHED &&
                    event.toolId in toolIds &&
                    (!requireSuccess || event.success)
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
