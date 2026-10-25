package de.heckenmann.visualagent.ui.conversation.live

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import de.heckenmann.visualagent.model.ToolLiveState
import de.heckenmann.visualagent.protocol.ToolActivity
import de.heckenmann.visualagent.protocol.ToolActivityPhase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Composition-owned snapshots; never accesses persistence or starts tool execution. */
internal class ConversationToolLiveState {
    val snapshots = mutableStateMapOf<String, ToolLiveState>()
    private val active = mutableSetOf<String>()
    private val finished = linkedSetOf<String>()

    /** Captured by background listeners before enqueueing a Main-dispatcher update. */
    @Volatile
    var generation: Long = 0
        private set

    /** Applies ordered runtime frames only after observing their start in this conversation. */
    fun accept(
        event: ToolActivity,
        expectedGeneration: Long = generation,
    ) {
        if (expectedGeneration != generation) return
        val key = toolLiveKey(event)
        when (event.phase) {
            ToolActivityPhase.STARTED -> {
                if (key in finished) return
                active += key
                event.liveState?.let { applySnapshot(key, it) }
            }
            ToolActivityPhase.UPDATED, ToolActivityPhase.FINISHED -> {
                if (event.phase == ToolActivityPhase.FINISHED) {
                    finished += key
                    while (finished.size > 256) finished.remove(finished.first())
                }
                if (key !in active) return
                event.liveState?.let { next ->
                    applySnapshot(key, next)
                }
                if (event.phase == ToolActivityPhase.FINISHED) active.remove(key)
            }
        }
        while (snapshots.size > 256) snapshots.remove(snapshots.keys.first())
    }

    private fun applySnapshot(
        key: String,
        next: ToolLiveState,
    ) {
        val previous = snapshots[key]
        if (previous == null ||
            (previous.executionId == next.executionId && previous.revision < next.revision && !previous.terminal)
        ) {
            snapshots[key] = next
        }
    }

    /** Invalidates in-flight frames when the user clears or replaces the conversation. */
    fun clear() {
        generation++
        active.clear()
        finished.clear()
        snapshots.clear()
    }
}

/** Supplies the existing conversation's snapshots to rows without tool-specific dispatch. */
internal val LocalToolLiveSnapshots = staticCompositionLocalOf<Map<String, ToolLiveState>> { emptyMap() }

/** Stable call correlation shared by a protocol frame and its persisted metadata. */
internal fun toolLiveKey(event: ToolActivity): String =
    event.executionId ?: listOf(
        event.toolId,
        event.requestId,
        event.parentAssistantTurnId,
        event.providerToolCallId,
        event.sequence?.toString(),
        event.batchId,
        event.batchSequence?.toString(),
    ).let { Json.encodeToString(it) }

/** Reads correlation without rewriting conversation content. */
internal fun toolLiveKey(metadata: String?): String? =
    runCatching {
        val values = Json.parseToJsonElement(metadata ?: return null).jsonObject
        values["executionId"]?.jsonPrimitive?.content?.let { return it }
        Json.encodeToString(
            listOf(
                "toolId",
                "requestId",
                "parentAssistantTurnId",
                "providerToolCallId",
                "sequence",
                "batchId",
                "batchSequence",
            ).map {
                values[it]?.jsonPrimitive?.content
            },
        )
    }.getOrNull()
