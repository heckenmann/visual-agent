package de.heckenmann.visualagent.workspace

import de.heckenmann.visualagent.error.WorkspaceFileException
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

/**
 * Reads common image dimensions from file headers without desktop UI image APIs.
 */
object ImageHeaderReader {
    /**
     * Reads dimensions for PNG, JPEG, or GIF images.
     *
     * @param path Image file to inspect
     * @return Image dimensions
     * @throws IllegalArgumentException when the image format is unsupported
     */
    fun dimensions(path: Path): ImageDimensions =
        Files.newInputStream(path).use { input ->
            val bytes = input.readNBytes(32)
            when {
                bytes.isPngHeader() -> ImageDimensions(width = bytes.readInt(16), height = bytes.readInt(20))
                bytes.isGifHeader() -> ImageDimensions(width = bytes.readLittleShort(6), height = bytes.readLittleShort(8))
                bytes.isJpegHeader() -> Files.newInputStream(path).use(::readJpegDimensions)
                else ->
                    throw WorkspaceFileException(
                        summary = "Unsupported image file",
                        detail = "Only PNG, JPEG, and GIF files can be inspected. Convert the image or choose a supported file.",
                        retryable = false,
                    )
            }
        }

    private fun readJpegDimensions(input: InputStream): ImageDimensions {
        val data = DataInputStream(input)
        require(data.readUnsignedShort() == JPEG_MAGIC) { "Unsupported image file" }
        while (true) {
            var markerPrefix = data.readUnsignedByte()
            while (markerPrefix != JPEG_MARKER_PREFIX) {
                markerPrefix = data.readUnsignedByte()
            }
            var marker = data.readUnsignedByte()
            while (marker == JPEG_MARKER_PREFIX) {
                marker = data.readUnsignedByte()
            }
            if (marker in JPEG_STANDALONE_MARKERS) continue
            val length = data.readUnsignedShort()
            require(length >= 2) { "Invalid JPEG segment" }
            if (marker in JPEG_START_OF_FRAME_MARKERS) {
                data.readUnsignedByte()
                val height = data.readUnsignedShort()
                val width = data.readUnsignedShort()
                return ImageDimensions(width = width, height = height)
            }
            data.skipFully(length - 2)
        }
    }

    private fun DataInputStream.skipFully(byteCount: Int) {
        var remaining = byteCount
        while (remaining > 0) {
            val skipped = skipBytes(remaining)
            if (skipped <= 0) throw EOFException("Unexpected end of JPEG data")
            remaining -= skipped
        }
    }

    private fun ByteArray.isPngHeader(): Boolean =
        size >= 24 &&
            this[0] == 0x89.toByte() &&
            this[1] == 0x50.toByte() &&
            this[2] == 0x4E.toByte() &&
            this[3] == 0x47.toByte()

    private fun ByteArray.isGifHeader(): Boolean =
        size >= 10 &&
            this[0] == 0x47.toByte() &&
            this[1] == 0x49.toByte() &&
            this[2] == 0x46.toByte()

    private fun ByteArray.isJpegHeader(): Boolean =
        size >= 2 &&
            this[0] == 0xff.toByte() &&
            this[1] == 0xd8.toByte()

    private fun ByteArray.readInt(offset: Int): Int =
        ((this[offset].toInt() and BYTE_MASK) shl 24) or
            ((this[offset + 1].toInt() and BYTE_MASK) shl 16) or
            ((this[offset + 2].toInt() and BYTE_MASK) shl 8) or
            (this[offset + 3].toInt() and BYTE_MASK)

    private fun ByteArray.readLittleShort(offset: Int): Int =
        (this[offset].toInt() and BYTE_MASK) or ((this[offset + 1].toInt() and BYTE_MASK) shl 8)

    private const val BYTE_MASK = 0xff
    private const val JPEG_MAGIC = 0xffd8
    private const val JPEG_MARKER_PREFIX = 0xff
    private val JPEG_STANDALONE_MARKERS = setOf(0x01, 0xd0, 0xd1, 0xd2, 0xd3, 0xd4, 0xd5, 0xd6, 0xd7, 0xd8, 0xd9)
    private val JPEG_START_OF_FRAME_MARKERS = setOf(0xc0, 0xc1, 0xc2, 0xc3, 0xc5, 0xc6, 0xc7, 0xc9, 0xca, 0xcb, 0xcd, 0xce, 0xcf)
}

/**
 * Basic image dimensions read from an image file header.
 */
data class ImageDimensions(
    /** Image width in pixels. */
    val width: Int,
    /** Image height in pixels. */
    val height: Int,
)
