package de.heckenmann.visualagent.testsupport

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.CRC32
import java.util.zip.Deflater

/**
 * Generates tiny PNG fixtures.
 */
object TestPng {
    /**
     * Writes a solid-color RGBA PNG.
     *
     * @param path Destination file
     * @param width Image width in pixels
     * @param height Image height in pixels
     */
    fun write(
        path: Path,
        width: Int,
        height: Int,
    ) {
        Files.write(path, bytes(width, height))
    }

    private fun bytes(
        width: Int,
        height: Int,
    ): ByteArray {
        val raw = mutableListOf<Byte>()
        repeat(height) {
            raw += 0
            repeat(width) {
                raw += 0x33
                raw += 0x66
                raw += 0x99.toByte()
                raw += 0xff.toByte()
            }
        }
        return buildList<Byte> {
            addAll(PNG_SIGNATURE.toList())
            addAll(chunk("IHDR", ihdr(width, height)).toList())
            addAll(chunk("IDAT", deflate(raw.toByteArray())).toList())
            addAll(chunk("IEND", ByteArray(0)).toList())
        }.toByteArray()
    }

    private fun ihdr(
        width: Int,
        height: Int,
    ): ByteArray =
        buildList<Byte> {
            addInt(width)
            addInt(height)
            add(8)
            add(6)
            add(0)
            add(0)
            add(0)
        }.toByteArray()

    private fun deflate(bytes: ByteArray): ByteArray {
        val deflater = Deflater()
        return try {
            deflater.setInput(bytes)
            deflater.finish()
            val buffer = ByteArray(1024)
            buildList<Byte> {
                while (!deflater.finished()) {
                    val count = deflater.deflate(buffer)
                    repeat(count) { add(buffer[it]) }
                }
            }.toByteArray()
        } finally {
            deflater.end()
        }
    }

    private fun chunk(
        type: String,
        data: ByteArray,
    ): ByteArray {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(data)
        return buildList<Byte> {
            addInt(data.size)
            addAll(typeBytes.toList())
            addAll(data.toList())
            addInt(crc.value.toInt())
        }.toByteArray()
    }

    private fun MutableList<Byte>.addInt(value: Int) {
        add(((value ushr 24) and 0xff).toByte())
        add(((value ushr 16) and 0xff).toByte())
        add(((value ushr 8) and 0xff).toByte())
        add((value and 0xff).toByte())
    }

    private val PNG_SIGNATURE = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
}
