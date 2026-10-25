package de.heckenmann.visualagent.ui.conversation.live

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import de.heckenmann.visualagent.protocol.ActivityPort
import de.heckenmann.visualagent.protocol.ClientImagePort
import de.heckenmann.visualagent.protocol.ConversationPort
import de.heckenmann.visualagent.ui.components.LocalClientImagePort
import de.heckenmann.visualagent.ui.components.LocalConversationPort
import de.heckenmann.visualagent.ui.conversation.ConversationUiState

/** Installs presentation-scoped conversation/media dependencies and execution actions. */
@Composable
internal fun ConversationPanelProviders(
    conversation: ConversationPort,
    clientImages: ClientImagePort?,
    activity: ActivityPort,
    state: ConversationUiState,
    content: @Composable () -> Unit,
) {
    val cancel = rememberToolCancellation(activity) { state.status = it }
    CompositionLocalProvider(
        LocalConversationPort provides conversation,
        LocalClientImagePort provides clientImages,
        LocalToolLiveSnapshots provides state.liveTools.snapshots,
        LocalToolCancellation provides cancel,
        content = content,
    )
}
