package de.heckenmann.visualagent.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.rememberStreamingMarkdownState
import de.heckenmann.visualagent.protocol.ClientImagePort
import de.heckenmann.visualagent.protocol.ConversationPort

/**
 * Renders an append-only Markdown stream with the renderer's incremental parser.
 *
 * The stream key must change for every new response. The parser then keeps completed
 * Markdown blocks stable while it updates only the unfinished tail for each new chunk.
 *
 * @param markdown Complete accumulated response content received so far
 * @param streamKey Stable identifier for one append-only response stream
 * @param modifier Optional Compose modifier
 * @param conversationPort Server boundary used to resolve image nodes
 * @param clientImagePort Client boundary used only for `client-file:` image nodes
 */
@Composable
internal fun ComposeStreamingMarkdown(
    markdown: String,
    streamKey: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    conversationPort: ConversationPort? = LocalConversationPort.current,
    clientImagePort: ClientImagePort? = LocalClientImagePort.current,
) {
    val imageTransformer = rememberImageTransformer(conversationPort, clientImagePort)
    key(streamKey) {
        val markdownState = rememberStreamingMarkdownState()
        LaunchedEffect(markdown) {
            val renderedContent = markdownState.content.toString()
            if (markdown.startsWith(renderedContent)) {
                markdown
                    .drop(renderedContent.length)
                    .takeIf(String::isNotEmpty)
                    ?.let { chunk -> markdownState.append(chunk) }
            }
        }
        Markdown(
            streamingMarkdownState = markdownState,
            modifier = modifier,
            imageTransformer = imageTransformer,
        )
    }
}
