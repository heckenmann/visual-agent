package de.heckenmann.visualagent.canvas

import de.heckenmann.visualagent.error.CanvasOperationException
import de.heckenmann.visualagent.image.RgbaPngEncoder
import de.heckenmann.visualagent.workspace.ImageHeaderReader
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.util.Base64
import java.util.zip.CRC32
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CanvasCaptureDecodeRegressionTest {
    @Test
    fun `rejects compressed PNG data with a valid header`() {
        val broken = png()
        var offset = 8
        while (offset < broken.size) {
            val length = ByteBuffer.wrap(broken).getInt(offset)
            val type = String(broken, offset + 4, 4, Charsets.US_ASCII)
            if (type == "IDAT") {
                broken[offset + 8] = 0
                broken[offset + 9] = 0
                val crc = CRC32().apply { update(broken, offset + 4, length + 4) }
                ByteBuffer.wrap(broken).putInt(offset + 8 + length, crc.value.toInt())
                break
            }
            offset += length + 12
        }
        assertEquals(2, ImageHeaderReader.dimensions(broken).width)

        assertFailsWith<CanvasOperationException> { capture(broken) }
    }

    @Test
    fun `rejects GIF decoder dimensions before allocating pixels even when its screen header fits`() {
        listOf(5000 to 5000, 3000 to 2000).forEach { (width, height) ->
            val gif = gifFrame(width, height)
            assertEquals(1, ImageHeaderReader.dimensions(gif).width)
            Data.makeFromBytes(gif).use { data ->
                Codec.makeFromData(data).use { codec ->
                    assertEquals(width, codec.width)
                    assertEquals(height, codec.height)
                }
            }

            val failure = assertFailsWith<CanvasOperationException> { capture(gif) }
            assertTrue(failure.message.orEmpty().contains("safe bounds"), "Reject the decoder geometry before reading pixels")
        }
    }

    @Test
    fun `bounded PNG JPEG and GIF content still decodes successfully`() {
        val jpeg =
            Image.makeFromEncoded(png()).use { image ->
                checkNotNull(image.encodeToData(EncodedImageFormat.JPEG)).use { it.bytes }
            }
        listOf(png(), jpeg, gifFrame(1, 1)).forEach { bytes ->
            val captured = capture(bytes)
            Image.makeFromEncoded(captured).use { image ->
                Bitmap.makeFromImage(image).use { bitmap ->
                    assertTrue(bitmap.getColor(30, 30) != BACKGROUND)
                }
            }
        }
    }

    private fun capture(bytes: ByteArray): ByteArray =
        CanvasPngRenderer.render(
            CanvasSnapshot(1, 100, true, figures = listOf(CanvasFigureSnapshot(0, "image", 10.0, 10.0, 40.0, 40.0, "fixture"))),
            100,
            80,
        ) { bytes }

    private fun png() = RgbaPngEncoder.encodeArgb(2, 2, IntArray(4) { 0xFFFF0000.toInt() })

    private fun gifFrame(
        width: Int,
        height: Int,
    ): ByteArray {
        val bytes = Base64.getDecoder().decode("R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAkQBADs=")
        val descriptor = 19
        check(bytes[descriptor] == 0x2c.toByte())
        ByteBuffer
            .wrap(bytes)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .putShort(descriptor + 5, width.toShort())
            .putShort(descriptor + 7, height.toShort())
        return bytes
    }

    private companion object {
        const val BACKGROUND = 0xFF191A21.toInt()
    }
}
