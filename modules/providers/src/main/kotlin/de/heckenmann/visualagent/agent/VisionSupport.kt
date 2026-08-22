package de.heckenmann.visualagent.agent

import org.springframework.ai.content.Media
import org.springframework.core.io.ByteArrayResource
import org.springframework.util.MimeType
import org.springframework.util.MimeTypeUtils
import java.util.Base64

/** Builds provider-neutral image payloads for vision requests. */
internal object VisionSupport {
    /**
     * Creates a Spring AI media object from raw image bytes.
     *
     * @param image Encoded image bytes
     * @return Media payload with detected MIME type
     */
    fun media(image: ByteArray): Media = Media(mimeType(image), ByteArrayResource(image))

    /** Returns a data URL suitable for provider protocols that accept inline images. */
    fun dataUrl(image: ByteArray): String = "data:${mimeType(image)};base64,${Base64.getEncoder().encodeToString(image)}"

    private fun mimeType(image: ByteArray): MimeType =
        when {
            image.startsWith(0x89, 0x50, 0x4E, 0x47) -> MimeTypeUtils.IMAGE_PNG
            image.startsWith(0xFF, 0xD8, 0xFF) -> MimeTypeUtils.IMAGE_JPEG
            image.startsWith(0x47, 0x49, 0x46) -> MimeTypeUtils.IMAGE_GIF
            else -> error("Unsupported image format; expected PNG, JPEG, or GIF")
        }

    private fun ByteArray.startsWith(vararg bytes: Int): Boolean =
        size >= bytes.size && bytes.indices.all { index -> this[index].toInt() and 0xff == bytes[index] }
}
