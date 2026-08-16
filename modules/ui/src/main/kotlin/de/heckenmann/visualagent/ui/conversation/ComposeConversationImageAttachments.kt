@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.conversation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.MAX_MARKDOWN_IMAGE_BYTES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Base64
import org.jetbrains.skia.Image as SkiaImage

/** Renders image attachments already validated and supplied by the application server. */
@Composable
internal fun ConversationImageAttachments(images: List<String>) {
    if (images.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        images.forEachIndexed { index, source ->
            var bitmap by remember(source) { mutableStateOf<ImageBitmap?>(null) }
            LaunchedEffect(source) {
                bitmap = withContext(Dispatchers.Default) { decodeEmbeddedImage(source) }
            }
            val currentBitmap = bitmap
            if (currentBitmap == null) {
                Text(
                    text = "Embedded image unavailable",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { contentDescription = "Embedded image ${index + 1} unavailable" },
                )
            } else {
                Image(
                    bitmap = currentBitmap,
                    contentDescription = "Embedded image ${index + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(max = MAX_IMAGE_HEIGHT),
                )
            }
        }
    }
}

private fun decodeEmbeddedImage(source: String): ImageBitmap? {
    val match = EMBEDDED_IMAGE_PATTERN.matchEntire(source.trim()) ?: return null
    val bytes = runCatching { Base64.getDecoder().decode(match.groupValues[1]) }.getOrNull() ?: return null
    if (bytes.isEmpty() || bytes.size.toLong() > MAX_MARKDOWN_IMAGE_BYTES) return null
    return runCatching { SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
}

private val EMBEDDED_IMAGE_PATTERN = Regex("data:image/(?:png|jpeg|gif);base64,([A-Za-z0-9+/=]+)", RegexOption.IGNORE_CASE)
private val MAX_IMAGE_HEIGHT = 1024.dp
