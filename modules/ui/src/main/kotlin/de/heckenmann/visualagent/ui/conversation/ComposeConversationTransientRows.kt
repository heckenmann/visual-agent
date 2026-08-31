package de.heckenmann.visualagent.ui.conversation

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.ConversationMessage

/** Renders the transient assistant row while a response is streamed. */
@Composable
internal fun streamingTimelineRow(
    item: ConversationTimelineItem.Streaming,
    onStatusChange: (String) -> Unit,
) {
    TransientConversationMessageGroupRow(
        message = ConversationMessage("assistant", item.content, id = item.id),
        isStreaming = true,
        onCopied = { onStatusChange("Copied assistant message") },
        modifier = Modifier.padding(top = 2.dp),
    )
}

/** Renders the transient user row until the corresponding entry is persisted. */
@Composable
internal fun pendingTimelineRow(
    item: ConversationTimelineItem.PendingUser,
    onStatusChange: (String) -> Unit,
) {
    TransientConversationMessageGroupRow(
        message = ConversationMessage("user", item.content, id = item.id),
        isStreaming = false,
        onCopied = { onStatusChange("Copied user message") },
        modifier = Modifier.padding(top = 10.dp),
    )
}
