package de.heckenmann.visualagent.workspace

import de.heckenmann.visualagent.testsupport.TestPng
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeBytes
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ImageHeaderReaderTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `reads png dimensions from header`() {
        val image = tempDir.resolve("sample.png")
        TestPng.write(image, width = 37, height = 19)

        val dimensions = ImageHeaderReader.dimensions(image)

        assertEquals(37, dimensions.width)
        assertEquals(19, dimensions.height)
    }

    @Test
    fun `reads gif dimensions from header`() {
        val image = tempDir.resolve("sample.gif")
        image.writeBytes(
            byteArrayOf(
                0x47,
                0x49,
                0x46,
                0x38,
                0x39,
                0x61,
                0x21,
                0x00,
                0x0B,
                0x00,
            ),
        )

        val dimensions = ImageHeaderReader.dimensions(image)

        assertEquals(33, dimensions.width)
        assertEquals(11, dimensions.height)
    }

    @Test
    fun `reads jpeg dimensions from start of frame segment`() {
        val image = tempDir.resolve("sample.jpg")
        image.writeBytes(
            byteArrayOf(
                0xff.toByte(),
                0xd8.toByte(),
                0xff.toByte(),
                0xe0.toByte(),
                0x00,
                0x04,
                0x00,
                0x00,
                0xff.toByte(),
                0xc0.toByte(),
                0x00,
                0x11,
                0x08,
                0x00,
                0x2A,
                0x00,
                0x18,
                0x03,
                0x01,
                0x11,
                0x00,
                0x02,
                0x11,
                0x00,
                0x03,
                0x11,
                0x00,
            ),
        )

        val dimensions = ImageHeaderReader.dimensions(image)

        assertEquals(24, dimensions.width)
        assertEquals(42, dimensions.height)
    }

    @Test
    fun `rejects unsupported image headers`() {
        val image = tempDir.resolve("sample.bin")
        image.writeBytes(byteArrayOf(1, 2, 3, 4))

        assertFailsWith<IllegalArgumentException> {
            ImageHeaderReader.dimensions(image)
        }
    }
}
