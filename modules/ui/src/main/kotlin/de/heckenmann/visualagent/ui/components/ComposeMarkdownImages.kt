package de.heckenmann.visualagent.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.model.ImageData
import com.mikepenz.markdown.model.ImageTransformer
import de.heckenmann.visualagent.protocol.ClientImagePort
import de.heckenmann.visualagent.protocol.ConversationImageResolution
import de.heckenmann.visualagent.protocol.ConversationImageSources
import de.heckenmann.visualagent.protocol.ConversationPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage

/** Creates a Markdown image transformer with explicit server and client source boundaries. */
@Composable
internal fun rememberImageTransformer(
    serverPort: ConversationPort?,
    clientPort: ClientImagePort?,
): ImageTransformer =
    remember(serverPort, clientPort) {
        if (serverPort == null && clientPort == null) {
            NoOpImageTransformer
        } else {
            BoundaryImageTransformer(serverPort, clientPort)
        }
    }

private object NoOpImageTransformer : ImageTransformer {
    @Composable
    override fun transform(link: String): ImageData? = null
}

private class BoundaryImageTransformer(
    private val serverPort: ConversationPort?,
    private val clientPort: ClientImagePort?,
) : ImageTransformer {
    @Composable
    override fun transform(link: String): ImageData {
        val source = link.removePrefix(INLINE_IMAGE_SOURCE_PREFIX)
        var state by remember(source) { mutableStateOf<ResolvedImageState>(ResolvedImageState.Loading) }
        LaunchedEffect(source) {
            val resolution =
                runCatching {
                    withContext(Dispatchers.IO) {
                        resolveImage(source)
                    }
                }.getOrElse { ConversationImageResolution.Rejected("Image could not be loaded") }
            state = resolution.toImageState()
        }
        return state.toImageData()
    }

    private suspend fun resolveImage(link: String): ConversationImageResolution =
        if (ConversationImageSources.isClientFile(link)) {
            clientPort?.resolveImage(link)
        } else {
            serverPort?.resolveImage(link)
        } ?: ConversationImageResolution.Rejected("Image loading is not available")

    private companion object {
        /**
         * The Markdown renderer uses this prefix as the inline-content key. It is not part of
         * the actual image source and must not cross the server/client image boundary.
         */
        const val INLINE_IMAGE_SOURCE_PREFIX = "MARKDOWN_IMAGE_URL_"
    }
}

private sealed interface ResolvedImageState {
    data object Loading : ResolvedImageState

    data class Loaded(
        val painter: Painter,
    ) : ResolvedImageState

    data class Failed(
        val reason: String,
    ) : ResolvedImageState
}

private fun ConversationImageResolution.toImageState(): ResolvedImageState =
    when (this) {
        is ConversationImageResolution.Loaded ->
            runCatching {
                ResolvedImageState.Loaded(SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap().let(::BitmapPainter))
            }.getOrElse { ResolvedImageState.Failed("Image format is not supported") }
        is ConversationImageResolution.Rejected -> ResolvedImageState.Failed(reason)
    }

@Composable
private fun ResolvedImageState.toImageData(): ImageData {
    val loaded = this as? ResolvedImageState.Loaded
    val failure = this as? ResolvedImageState.Failed
    val description =
        when {
            loaded != null -> "Image"
            failure != null -> "Image unavailable: ${failure.reason}"
            else -> "Image loading"
        }
    val isLoading = this is ResolvedImageState.Loading
    val modifier =
        if (loaded != null) {
            Modifier.fillMaxWidth().heightIn(max = MAX_MARKDOWN_IMAGE_HEIGHT)
        } else {
            Modifier
                .fillMaxWidth()
                .height(IMAGE_FALLBACK_HEIGHT)
                .background(
                    if (isLoading) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                )
        }
    return ImageData(
        painter = loaded?.painter ?: ColorPainter(MaterialTheme.colorScheme.surface),
        modifier = modifier,
        contentDescription = description,
        contentScale = ContentScale.Fit,
    )
}

private val MAX_MARKDOWN_IMAGE_HEIGHT = 1024.dp
private val IMAGE_FALLBACK_HEIGHT = 64.dp
