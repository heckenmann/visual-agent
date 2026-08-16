package de.heckenmann.visualagent.desktop

import de.heckenmann.visualagent.protocol.ClientImagePort
import de.heckenmann.visualagent.protocol.ConversationImageResolution
import de.heckenmann.visualagent.protocol.ConversationImageSources
import de.heckenmann.visualagent.protocol.MAX_MARKDOWN_IMAGE_BYTES
import de.heckenmann.visualagent.protocol.MAX_MARKDOWN_IMAGE_DIMENSION
import de.heckenmann.visualagent.protocol.MAX_MARKDOWN_IMAGE_PIXELS
import de.heckenmann.visualagent.workspace.ImageDimensions
import de.heckenmann.visualagent.workspace.ImageHeaderReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.tika.Tika
import java.nio.file.Files
import java.nio.file.Path

/** Reads explicitly client-local Markdown images for the desktop presentation. */
internal class LocalClientImagePort(
    private val mimeDetector: Tika = Tika(),
) : ClientImagePort {
    /** Resolves a `client-file:` source without sending it to the application server. */
    override suspend fun resolveImage(source: String): ConversationImageResolution =
        withContext(Dispatchers.IO) {
            resolveLocalFile(source)
        }

    private fun resolveLocalFile(source: String): ConversationImageResolution {
        val normalized = source.trim()
        if (!ConversationImageSources.isClientFile(normalized)) {
            return rejected("Image source is not client-local")
        }
        val pathText = normalized.substring(ConversationImageSources.CLIENT_FILE_PREFIX.length).trim()
        if (pathText.isBlank()) return rejected("Client image path is empty")
        val path = runCatching { Path.of(pathText) }.getOrNull() ?: return rejected("Client image path is invalid")
        if (!runCatching { Files.isRegularFile(path) }.getOrDefault(false)) {
            return rejected("Client image was not found")
        }
        val size = runCatching { Files.size(path) }.getOrDefault(Long.MAX_VALUE)
        if (size <= 0L || size > MAX_MARKDOWN_IMAGE_BYTES) return rejected("Image is too large or empty")
        val bytes =
            runCatching {
                Files.newInputStream(path).use { input -> input.readNBytes(MAX_MARKDOWN_IMAGE_BYTES.toInt() + 1) }
            }.getOrNull() ?: return rejected("Client image could not be read")
        if (bytes.size.toLong() > MAX_MARKDOWN_IMAGE_BYTES) return rejected("Image is too large")
        val mimeType =
            runCatching { mimeDetector.detect(bytes).lowercase() }.getOrNull()
                ?: return rejected("Image type could not be detected")
        if (mimeType !in SUPPORTED_IMAGE_TYPES) return rejected("Image type is not supported")
        val dimensions = runCatching { ImageHeaderReader.dimensions(bytes) }.getOrNull() ?: return rejected("Image dimensions are invalid")
        if (!dimensions.areSafe()) return rejected("Image dimensions are too large")
        return ConversationImageResolution.Loaded(mimeType, bytes, dimensions.width, dimensions.height)
    }

    private fun ImageDimensions.areSafe(): Boolean =
        width > 0 &&
            height > 0 &&
            width <= MAX_MARKDOWN_IMAGE_DIMENSION &&
            height <= MAX_MARKDOWN_IMAGE_DIMENSION &&
            width.toLong() * height.toLong() <= MAX_MARKDOWN_IMAGE_PIXELS

    private fun rejected(reason: String): ConversationImageResolution.Rejected = ConversationImageResolution.Rejected(reason)

    private companion object {
        val SUPPORTED_IMAGE_TYPES = setOf("image/png", "image/jpeg", "image/gif")
    }
}
