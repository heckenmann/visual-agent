package de.heckenmann.visualagent.image

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream

/**
 * Encodes ARGB pixels into PNG bytes without desktop toolkit image APIs.
 */
object RgbaPngEncoder {
    /**
     * Encodes a full image as an RGBA PNG.
     *
     * @param width Pixel width
     * @param height Pixel height
     * @param pixels ARGB pixels in row-major order
     * @return PNG bytes
     */
    fun encodeArgb(
        width: Int,
        height: Int,
        pixels: IntArray,
    ): ByteArray {
        require(width > 0) { "PNG width must be positive" }
        require(height > 0) { "PNG height must be positive" }
        require(pixels.size == width * height) { "PNG pixel buffer size does not match dimensions" }
        val raw = ByteArrayOutputStream((width * height * 4) + height)
        for (y in 0 until height) {
            raw.write(0)
            for (x in 0 until width) {
                val pixel = pixels[y * width + x]
                raw.write((pixel ushr 16) and 0xFF)
                raw.write((pixel ushr 8) and 0xFF)
                raw.write(pixel and 0xFF)
                raw.write((pixel ushr 24) and 0xFF)
            }
        }
        val compressed = ByteArrayOutputStream()
        DeflaterOutputStream(compressed).use { it.write(raw.toByteArray()) }
        return ByteArrayOutputStream().use { output ->
            output.write(PNG_SIGNATURE)
            output.writeChunk("IHDR") {
                writeInt(width)
                writeInt(height)
                writeByte(8)
                writeByte(6)
                writeByte(0)
                writeByte(0)
                writeByte(0)
            }
            output.writeChunk("IDAT") { write(compressed.toByteArray()) }
            output.writeChunk("IEND") {}
            output.toByteArray()
        }
    }

    private fun ByteArrayOutputStream.writeChunk(
        type: String,
        writeData: DataOutputStream.() -> Unit,
    ) {
        val payload = ByteArrayOutputStream()
        DataOutputStream(payload).use(writeData)
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        val data = payload.toByteArray()
        DataOutputStream(this).writeInt(data.size)
        write(typeBytes)
        write(data)
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(data)
        DataOutputStream(this).writeInt(crc.value.toInt())
    }

    private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
}
