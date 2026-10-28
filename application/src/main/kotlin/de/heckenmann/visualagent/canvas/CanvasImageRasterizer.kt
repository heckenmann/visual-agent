package de.heckenmann.visualagent.canvas

import de.heckenmann.visualagent.error.CanvasOperationException
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect

/** Fully decodes a bounded managed image before compositing it into a capture. */
internal object CanvasImageRasterizer {
    fun draw(
        canvas: Canvas,
        reference: String,
        bounds: Rect,
        imageBytes: (String) -> ByteArray,
    ) {
        try {
            val bytes = imageBytes(reference)
            require(bytes.size <= CanvasCaptureLimits.MAX_IMAGE_BYTES) { "Canvas image exceeds the encoded byte limit" }
            Data.makeFromBytes(bytes).use { data ->
                Codec.makeFromData(data).use { codec ->
                    require(codec.encodedImageFormat in SUPPORTED_FORMATS) { "Unsupported canvas image format" }
                    // GIF frame bounds can exceed its logical-screen header. Only the decoder's info authorizes allocation.
                    CanvasCaptureLimits.validateSize(codec.width, codec.height)
                    Bitmap().use { bitmap ->
                        check(bitmap.allocN32Pixels(codec.width, codec.height)) { "Could not allocate the bounded image bitmap" }
                        // Unlike deferred Image decoding during drawing, Codec.readPixels rejects incomplete/corrupt input.
                        codec.readPixels(bitmap)
                        Image.makeFromBitmap(bitmap).use { image ->
                            val scale = minOf(bounds.width / image.width, bounds.height / image.height)
                            val width = image.width * scale
                            val height = image.height * scale
                            canvas.drawImageRect(
                                image,
                                Rect.makeXYWH(
                                    bounds.left + (bounds.width - width) / 2,
                                    bounds.top + (bounds.height - height) / 2,
                                    width,
                                    height,
                                ),
                            )
                        }
                    }
                }
            }
        } catch (failure: Exception) {
            if (failure is CanvasOperationException) throw failure
            throw CanvasOperationException(
                summary = "Canvas image could not be captured",
                detail =
                    "Use a readable, complete managed PNG, JPEG, or GIF image of at most 8 MiB, " +
                        "4096 pixels per dimension and 4194304 pixels in total.",
            )
        }
    }

    private val SUPPORTED_FORMATS = setOf(EncodedImageFormat.PNG, EncodedImageFormat.JPEG, EncodedImageFormat.GIF)
}
