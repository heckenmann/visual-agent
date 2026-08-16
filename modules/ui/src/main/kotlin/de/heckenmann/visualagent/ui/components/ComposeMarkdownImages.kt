package de.heckenmann.visualagent.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.sync.Semaphore
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
    private val budget = MarkdownImageLoadBudget()

    @Composable
    override fun transform(link: String): ImageData {
        val source = link.removePrefix(INLINE_IMAGE_SOURCE_PREFIX)
        var state by remember(source) { mutableStateOf<ResolvedImageState>(ResolvedImageState.Loading) }
        var reservation by remember(source) { mutableStateOf<MarkdownImageLoadBudget.Reservation?>(null) }
        DisposableEffect(source) {
            onDispose { reservation?.release() }
        }
        LaunchedEffect(source) {
            val permit = budget.tryAcquire()
            if (permit == null) {
                state = ResolvedImageState.Failed("Too many images are loading")
                return@LaunchedEffect
            }
            var keepReservation = false
            var nextReservation: MarkdownImageLoadBudget.Reservation? = null
            try {
                val resolution =
                    runCatching {
                        withContext(Dispatchers.IO) {
                            resolveImage(source)
                        }
                    }.getOrElse { ConversationImageResolution.Rejected("Image could not be loaded") }
                val loaded = resolution as? ConversationImageResolution.Loaded
                if (loaded != null) {
                    nextReservation = budget.tryReserve(loaded.bytes.size.toLong(), loaded.declaredPixels())
                    if (nextReservation == null) {
                        state = ResolvedImageState.Failed("Image budget exceeded")
                        return@LaunchedEffect
                    }
                }
                val nextState = withContext(Dispatchers.Default) { resolution.toImageState() }
                val candidateReservation = nextReservation
                if (nextState is ResolvedImageState.Loaded && candidateReservation != null) {
                    if (!candidateReservation.ensurePixels(nextState.decodedPixels)) {
                        state = ResolvedImageState.Failed("Image budget exceeded")
                        return@LaunchedEffect
                    }
                    reservation = candidateReservation
                    keepReservation = true
                }
                state = nextState
            } finally {
                if (!keepReservation) nextReservation?.release()
                permit.release()
            }
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
        val decodedPixels: Long,
    ) : ResolvedImageState

    data class Failed(
        val reason: String,
    ) : ResolvedImageState
}

private fun ConversationImageResolution.toImageState(): ResolvedImageState =
    when (this) {
        is ConversationImageResolution.Loaded ->
            runCatching {
                val image = SkiaImage.makeFromEncoded(bytes)
                ResolvedImageState.Loaded(
                    painter = image.toComposeImageBitmap().let(::BitmapPainter),
                    decodedPixels = image.width.toLong() * image.height.toLong(),
                )
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
        painter = loaded?.painter ?: ColorPainter(Color.Transparent),
        modifier = modifier,
        contentDescription = description,
        contentScale = ContentScale.Fit,
    )
}

private val MAX_MARKDOWN_IMAGE_HEIGHT = 1024.dp
private val IMAGE_FALLBACK_HEIGHT = 64.dp

/** Bounds concurrent and aggregate image resources for one Markdown message. */
internal class MarkdownImageLoadBudget(
    private val maxConcurrent: Int = MAX_CONCURRENT_MARKDOWN_IMAGE_LOADS,
    private val maxBytes: Long = MAX_MARKDOWN_IMAGE_BYTES_PER_MESSAGE,
    private val maxPixels: Long = MAX_MARKDOWN_IMAGE_PIXELS_PER_MESSAGE,
) {
    private val permits = Semaphore(maxConcurrent)
    private var reservedBytes = 0L
    private var reservedPixels = 0L

    /** Acquires one slot for a fetch and decode operation, or returns null when saturated. */
    fun tryAcquire(): Permit? = if (permits.tryAcquire()) Permit() else null

    /** Reserves encoded bytes and any dimensions known before decoding. */
    fun tryReserve(
        encodedBytes: Long,
        decodedPixels: Long,
    ): Reservation? =
        synchronized(this) {
            if (
                encodedBytes < 0L ||
                decodedPixels < 0L ||
                reservedBytes + encodedBytes > maxBytes ||
                reservedPixels + decodedPixels > maxPixels
            ) {
                null
            } else {
                reservedBytes += encodedBytes
                reservedPixels += decodedPixels
                Reservation(encodedBytes, decodedPixels)
            }
        }

    /** Represents a reservation retained while the decoded painter remains visible. */
    inner class Reservation(
        private val encodedBytes: Long,
        private var decodedPixels: Long,
    ) {
        private var released = false

        /** Confirms the decoded pixel count when the boundary had no dimensions. */
        fun ensurePixels(totalPixels: Long): Boolean =
            synchronized(this@MarkdownImageLoadBudget) {
                val additionalPixels = totalPixels - decodedPixels
                if (released || totalPixels < 0L || additionalPixels < 0L || reservedPixels + additionalPixels > maxPixels) {
                    false
                } else {
                    reservedPixels += additionalPixels
                    decodedPixels = totalPixels
                    true
                }
            }

        /** Releases the reservation exactly once. */
        fun release() =
            synchronized(this@MarkdownImageLoadBudget) {
                if (!released) {
                    reservedBytes -= encodedBytes
                    reservedPixels -= decodedPixels
                    released = true
                }
            }
    }

    /** Releases a fetch/decode slot. */
    inner class Permit {
        private var released = false

        /** Releases this slot exactly once. */
        fun release() =
            synchronized(this@MarkdownImageLoadBudget) {
                if (!released) {
                    permits.release()
                    released = true
                }
            }
    }

    private companion object {
        const val MAX_CONCURRENT_MARKDOWN_IMAGE_LOADS = 4
        const val MAX_MARKDOWN_IMAGE_BYTES_PER_MESSAGE = 64L * 1024L * 1024L
        const val MAX_MARKDOWN_IMAGE_PIXELS_PER_MESSAGE = 128_000_000L
    }
}

private fun ConversationImageResolution.Loaded.declaredPixels(): Long =
    if (width > 0 && height > 0) width.toLong() * height.toLong() else 0L
