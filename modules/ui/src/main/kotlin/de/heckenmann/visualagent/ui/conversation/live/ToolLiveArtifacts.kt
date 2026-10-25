package de.heckenmann.visualagent.ui.conversation.live

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import de.heckenmann.visualagent.model.ToolLiveArtifact
import de.heckenmann.visualagent.protocol.ConversationImageSources
import de.heckenmann.visualagent.ui.components.ActionIconButton
import de.heckenmann.visualagent.ui.components.LocalConversationPort
import de.heckenmann.visualagent.ui.components.rememberImageTransformer

/** Resolves only explicitly opened registered image artifacts through the existing server boundary. */
@Composable
internal fun ToolLiveArtifacts(
    artifacts: List<ToolLiveArtifact>,
    identity: String,
    terminal: Boolean,
) {
    val transformer = rememberImageTransformer(LocalConversationPort.current, null)
    artifacts.forEach { artifact ->
        var expanded by remember(artifact.workspaceFileId) { mutableStateOf(false) }
        Column {
            Row {
                LiveAnimatedText(
                    artifact.label,
                    identity,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                    terminal = terminal,
                )
                if (artifact.mimeType.startsWith("image/")) {
                    ActionIconButton(
                        icon = Icons.Filled.Image,
                        description = if (expanded) "Hide artifact preview" else "Preview artifact",
                        onClick = { expanded = !expanded },
                    )
                }
            }
            if (expanded) {
                transformer.transform(ConversationImageSources.WORKSPACE_ID_PREFIX + artifact.workspaceFileId)?.let { image ->
                    Image(
                        painter = image.painter,
                        contentDescription = image.contentDescription,
                        modifier = image.modifier,
                        contentScale = image.contentScale,
                    )
                }
            }
        }
    }
}
