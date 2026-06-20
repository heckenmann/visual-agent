package de.heckenmann.visualagent.image

import javafx.scene.image.Image
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater

/**
 * Minimal PNG encoder for JavaFX images.
 */
object PngEncoder {
    /**
     * Encodes a JavaFX image as an 8-bit RGBA PNG.
     *
     * @param image Source image
     * @return PNG bytes
     */
    fun encode(image: Image): ByteArray {
        val width = image.width.toInt()
        val height = image.height.toInt()
        require(width > 0 && height > 0) { "Image must have positive dimensions" }
        val reader = image.pixelReader ?: throw IllegalArgumentException("Image pixels are not readable")
        val raw = ByteArrayOutputStream((width * 4 + 1) * height)
        repeat(height) { y ->
            raw.write(0)
            repeat(width) { x ->
                val argb = reader.getArgb(x, y)
                raw.write((argb ushr 16) and 0xff)
                raw.write((argb ushr 8) and 0xff)
                raw.write(argb and 0xff)
                raw.write((argb ushr 24) and 0xff)
            }
        }
        return ByteArrayOutputStream().use { output ->
            output.write(PNG_SIGNATURE)
            output.writeChunk("IHDR", ihdr(width, height))
            output.writeChunk("IDAT", deflate(raw.toByteArray()))
            output.writeChunk("IEND", ByteArray(0))
            output.toByteArray()
        }
    }

    private fun ihdr(
        width: Int,
        height: Int,
    ): ByteArray =
        ByteArrayOutputStream(13).use { output ->
            output.writeInt(width)
            output.writeInt(height)
            output.write(8)
            output.write(6)
            output.write(0)
            output.write(0)
            output.write(0)
            output.toByteArray()
        }

    private fun deflate(bytes: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION)
        return try {
            deflater.setInput(bytes)
            deflater.finish()
            val buffer = ByteArray(8192)
            ByteArrayOutputStream().use { output ->
                while (!deflater.finished()) {
                    output.write(buffer, 0, deflater.deflate(buffer))
                }
                output.toByteArray()
            }
        } finally {
            deflater.end()
        }
    }

    private fun ByteArrayOutputStream.writeChunk(
        type: String,
        data: ByteArray,
    ) {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        writeInt(data.size)
        write(typeBytes)
        write(data)
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(data)
        writeInt(crc.value.toInt())
    }

    private fun ByteArrayOutputStream.writeInt(value: Int) {
        write((value ushr 24) and 0xff)
        write((value ushr 16) and 0xff)
        write((value ushr 8) and 0xff)
        write(value and 0xff)
    }

    private val PNG_SIGNATURE = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
}
