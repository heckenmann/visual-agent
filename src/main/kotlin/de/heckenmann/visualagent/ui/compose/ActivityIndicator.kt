@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.agent.tools.ToolCallEvent
import de.heckenmann.visualagent.agent.tools.ToolCallPhase
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/**
 * Aggregated count of pending agent activities.
 *
 * @property streamingRequestIds Active chat request IDs that have not yet received a final chunk
 * @property runningAgentIds Sub-agent IDs whose job is currently queued or executing
 * @property pendingToolIds Tool IDs with a STARTED event but no matching FINISHED event
 * @property settingsLoading True while a settings refresh (models, details) is in flight
 */
data class InFlightState(
    val streamingRequestIds: Set<String> = emptySet(),
    val runningAgentIds: Set<String> = emptySet(),
    val pendingToolIds: Set<String> = emptySet(),
    val settingsLoading: Boolean = false,
) {
    /** Total number of activities that are currently in flight. */
    val totalActive: Int
        get() =
            streamingRequestIds.size +
                runningAgentIds.size +
                pendingToolIds.size +
                if (settingsLoading) 1 else 0

    /** Short human-readable summary of all in-flight activities. */
    fun describe(): String {
        if (totalActive == 0) return "Idle"
        val parts = mutableListOf<String>()
        if (streamingRequestIds.isNotEmpty()) {
            val n = streamingRequestIds.size
            parts += if (n == 1) "1 chat stream" else "$n chat streams"
        }
        if (runningAgentIds.isNotEmpty()) {
            val n = runningAgentIds.size
            parts += if (n == 1) "1 sub-agent" else "$n sub-agents"
        }
        if (pendingToolIds.isNotEmpty()) {
            val n = pendingToolIds.size
            parts += if (n == 1) "1 tool call" else "$n tool calls"
        }
        if (settingsLoading) {
            parts += "refreshing settings"
        }
        return parts.joinToString(separator = ", ")
    }
}

/**
 * Mutable holder for [InFlightState] that panels can update from coroutines.
 */
class InFlightStateHolder internal constructor() {
    internal val state: MutableState<InFlightState> = mutableStateOf(InFlightState())
    private val pendingToolKeys = mutableStateListOf<String>()

    /** Adds a streaming chat request id. No-op if already present. */
    fun markStreamStart(requestId: String) {
        val current = state.value
        if (requestId in current.streamingRequestIds) return
        state.value = current.copy(streamingRequestIds = current.streamingRequestIds + requestId)
    }

    /** Removes a streaming chat request id. No-op if absent. */
    fun markStreamEnd(requestId: String) {
        val current = state.value
        if (requestId !in current.streamingRequestIds) return
        state.value = current.copy(streamingRequestIds = current.streamingRequestIds - requestId)
    }

    /** Marks a sub-agent id as running. */
    fun markAgentStart(agentId: String) {
        val current = state.value
        if (agentId in current.runningAgentIds) return
        state.value = current.copy(runningAgentIds = current.runningAgentIds + agentId)
    }

    /** Marks a sub-agent id as no longer running. */
    fun markAgentEnd(agentId: String) {
        val current = state.value
        if (agentId !in current.runningAgentIds) return
        state.value = current.copy(runningAgentIds = current.runningAgentIds - agentId)
    }

    /** Sets the settings-loading flag. */
    fun setSettingsLoading(loading: Boolean) {
        val current = state.value
        if (current.settingsLoading == loading) return
        state.value = current.copy(settingsLoading = loading)
    }

    /**
     * Internal API used by [rememberInFlightState] to publish a [ToolCallEvent]
     * received on the [ToolEventBus]. The event is keyed by `toolId@requestId` so
     * concurrent calls of the same tool do not collapse into a single entry.
     *
     * A FINISHED event without a matching STARTED is ignored: the indicator
     * only reflects activities we know to be running.
     */
    internal fun onToolEvent(event: ToolCallEvent) {
        val key = event.toolId + "@" + (event.context["requestId"]?.toString() ?: event.functionName)
        when (event.phase) {
            ToolCallPhase.STARTED -> {
                if (key !in pendingToolKeys) {
                    pendingToolKeys += key
                    publishTools()
                }
            }
            ToolCallPhase.FINISHED -> {
                if (pendingToolKeys.remove(key)) {
                    publishTools()
                }
            }
        }
    }

    private fun publishTools() {
        val ids = pendingToolKeys.map { it.substringBefore('@') }.toSet()
        val current = state.value
        if (current.pendingToolIds == ids) return
        state.value = current.copy(pendingToolIds = ids)
    }
}

/**
 * Creates and remembers an [InFlightStateHolder] that listens to [toolEventBus]
 * and keeps its tool-call set in sync.
 *
 * The holder is owned by the caller. Tool events flow through a coroutine on the
 * Compose main dispatcher so the underlying `mutableStateOf` writes happen on
 * the recomposition thread.
 *
 * @param toolEventBus Spring-managed tool event bus
 * @return Mutable holder that panels can update
 */
@Composable
fun rememberInFlightState(toolEventBus: ToolEventBus): InFlightStateHolder {
    val holder = remember { InFlightStateHolder() }
    val collector = remember { MutableSharedFlow<ToolCallEvent>(extraBufferCapacity = 64) }
    val scope = rememberCoroutineScope()
    DisposableEffect(toolEventBus, holder) {
        val handle =
            toolEventBus.addListener { event ->
                collector.tryEmit(event)
            }
        val job =
            scope.launch {
                collector.collect { event -> holder.onToolEvent(event) }
            }
        onDispose {
            job.cancel()
            handle.close()
        }
    }
    return holder
}

/**
 * Header activity indicator. Renders nothing when [state.totalActive] is zero so
 * no layout space is reserved. Fades in and out as activities start and end.
 *
 * The pulse period scales with the number of in-flight activities: a single
 * activity pulses at 900 ms, two or three at 600 ms, and four or more at
 * 400 ms so a busy session is more visually salient.
 *
 * A tooltip reveals the concrete list of streams, sub-agents, tool calls, and
 * the settings refresh state. The container is announced as a status region
 * to screen readers.
 *
 * @param state Aggregated in-flight state
 * @param modifier Modifier applied to the indicator row
 *
 * Use cases: UC-0000072.
 */
@Composable
internal fun InFlightIndicator(
    state: InFlightState,
    modifier: Modifier = Modifier,
) {
    val active = state.totalActive
    AnimatedVisibility(
        visible = active > 0,
        enter = fadeIn(animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)),
        exit = fadeOut(animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)),
        modifier = modifier,
    ) {
        val description = remember(active) { "Agent busy: ${state.describe()}" }
        InFlightIndicatorContent(state = state, contentDescription = description)
    }
}

@Composable
private fun InFlightIndicatorContent(
    state: InFlightState,
    contentDescription: String,
) {
    val active = state.totalActive
    val pulseDuration =
        when {
            active >= 4 -> 400
            active >= 2 -> 600
            else -> 900
        }
    val transition = rememberInfiniteTransition(label = "in-flight")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = pulseDuration, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "in-flight-pulse",
    )
    val dotCount = active.coerceIn(1, 3)
    val accent = Color(0xFFBD93F9)
    ActionTooltip(
        description = state.describe(),
        modifier =
            Modifier
                .semantics(mergeDescendants = true) {
                    liveRegion = LiveRegionMode.Polite
                    this.contentDescription = contentDescription
                },
    ) {
        Row(
            modifier =
                Modifier
                    .background(Color(0xFF44475A).copy(alpha = 0.35f), CircleShape)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InFlightGlyph(accent = accent, pulse = pulse)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(dotCount) { index ->
                    Box(
                        modifier =
                            Modifier
                                .size(width = 14.dp, height = 2.dp)
                                .alpha(((pulse - index * 0.18f).coerceIn(0.25f, 1f)))
                                .background(accent.copy(alpha = 0.8f), CircleShape),
                    )
                }
            }
        }
    }
}

@Composable
private fun InFlightGlyph(
    accent: Color,
    pulse: Float,
) {
    val haloAlpha = 0.25f + pulse * 0.35f
    Box(
        modifier = Modifier.size(14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(14.dp)
                    .alpha(haloAlpha)
                    .background(accent.copy(alpha = 0.35f), CircleShape),
        )
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .alpha(pulse)
                    .background(accent, CircleShape),
        )
    }
}
