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
    private val awaitingStart = mutableMapOf<String, ToolLiveState>()
    private var snapshotSequence = 0L
    private var synchronization: LiveSnapshotSync? = null

    /** Captured by background listeners before enqueueing a Main-dispatcher update. */
    @Volatile
    var generation: Long = 0
        private set

    /** Opens the bounded window in which updates may precede the initial active-call snapshot. */
    fun beginSnapshotSync(): LiveSnapshotSync =
        LiveSnapshotSync(generation, ++snapshotSequence).also {
            awaitingStart.clear()
            synchronization = it
        }

    /** Merges initial starts with newer buffered revisions without resurrecting completed calls. */
    fun completeSnapshotSync(
        events: List<ToolActivity>,
        token: LiveSnapshotSync,
    ) {
        if (synchronization != token || generation != token.generation) return
        events.forEach { accept(it, token.generation) }
        cancelSnapshotSync(token)
    }

    /** Discards unclaimed updates on disposal, failure or replacement of the subscription. */
    fun cancelSnapshotSync(token: LiveSnapshotSync) {
        if (synchronization != token) return
        awaitingStart.clear()
        synchronization = null
    }

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
                awaitingStart.remove(key)?.let { applySnapshot(key, it) }
            }
            ToolActivityPhase.UPDATED, ToolActivityPhase.FINISHED -> {
                if (event.phase == ToolActivityPhase.FINISHED) {
                    finished += key
                    awaitingStart.remove(key)
                    while (finished.size > 256) finished.remove(finished.first())
                }
                if (key !in active) {
                    if (event.phase == ToolActivityPhase.UPDATED && synchronization != null && key !in finished) {
                        event.liveState?.let { next ->
                            val previous = awaitingStart[key]
                            if (previous == null || previous.executionId == next.executionId && previous.revision < next.revision) {
                                awaitingStart[key] = next
                            }
                        }
                        while (awaitingStart.size > 256) awaitingStart.remove(awaitingStart.keys.first())
                    }
                    return
                }
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
        awaitingStart.clear()
        synchronization = null
        active.clear()
        finished.clear()
        snapshots.clear()
    }
}

/** Identifies one initial fetch independently of history resets and later replacement subscriptions. */
internal data class LiveSnapshotSync(
    val generation: Long,
    val sequence: Long,
)

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
