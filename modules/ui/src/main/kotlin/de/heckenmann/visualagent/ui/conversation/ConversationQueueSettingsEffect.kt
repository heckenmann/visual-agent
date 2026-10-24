package de.heckenmann.visualagent.ui.conversation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import de.heckenmann.visualagent.protocol.SettingsPort
import kotlinx.coroutines.launch

/** Applies live queue preferences without recreating the composer or discarding queued messages. */
@Composable
internal fun ConversationQueueSettingsEffect(
    settings: SettingsPort,
    queue: MessageQueue,
) {
    val scope = rememberCoroutineScope()
    val revision = remember(settings) { intArrayOf(0) }
    DisposableEffect(settings, queue) {
        val listener =
            settings.addChangeListener { snapshot ->
                scope.launch {
                    revision[0]++
                    queue.flushMode = queueFlushMode(snapshot.queueFlushMode)
                }
            }
        onDispose { listener.close() }
    }
    LaunchedEffect(settings, queue) {
        val before = revision[0]
        val snapshot = settings.snapshotAsync()
        if (before == revision[0]) queue.flushMode = queueFlushMode(snapshot.queueFlushMode)
    }
}

/** Treats an unknown persisted queue mode like the default rather than breaking message submission. */
private fun queueFlushMode(value: String): QueueFlushMode =
    QueueFlushMode.entries.firstOrNull { it.name == value } ?: QueueFlushMode.ONE_BY_ONE
