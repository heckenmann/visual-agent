package de.heckenmann.visualagent.desktop

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.IRect
import org.jetbrains.skia.Image
import kotlin.test.Test
import kotlin.test.assertEquals

/** Checks matching, displaced tiles, and unavailable capture data without a screen capture. */
class StartupRenderPatternTest {
    @Test
    fun `valid colors tolerate small color differences at different display scales`() {
        for (tileSize in listOf(16, 24, 37, 48)) {
            assertEquals(StartupPatternResult.MATCH, inspectStartupPattern(capture(tileSize)))
        }
    }

    @Test
    fun `a displaced row of blocks fails the pattern check`() {
        assertEquals(StartupPatternResult.MISMATCH, inspectStartupPattern(capture(tileSize = 24, shiftSecondRow = true)))
    }

    @Test
    fun `unavailable invalid and uncropped images leave visual integrity unknown`() {
        assertEquals(StartupPatternResult.UNKNOWN, inspectStartupPattern(""))
        assertEquals(StartupPatternResult.UNKNOWN, inspectStartupPattern(byteArrayOf()))
        Bitmap().use { bitmap ->
            bitmap.allocN32Pixels(width = 136, height = 72)
            assertEquals(StartupPatternResult.UNKNOWN, inspectStartupPattern(encode(bitmap)))
        }
    }

    private fun capture(
        tileSize: Int,
        shiftSecondRow: Boolean = false,
    ): ByteArray {
        // These encoded fixtures have independently chosen RGB offsets from the actual marker.
        val tiles =
            listOf(
                listOf(0xDC3448, 0x1D8850, 0x285AD5, 0xF0CB42),
                listOf(0x8812DA, 0xF0CB42, 0x2CC7D5, 0x1D8850),
                listOf(0x285AD5, 0xDC3448, 0x1D8850, 0x8812DA),
            )
        return Bitmap().use { bitmap ->
            bitmap.allocN32Pixels(4 * tileSize, 3 * tileSize, opaque = true)
            tiles.forEachIndexed { row, colors ->
                colors.forEachIndexed { column, color ->
                    val x = (if (shiftSecondRow && row == 1) (column + 1) % 4 else column) * tileSize
                    bitmap.erase(color or 0xFF000000.toInt(), IRect.makeXYWH(x, row * tileSize, tileSize, tileSize))
                }
            }
            encode(bitmap)
        }
    }

    private fun encode(bitmap: Bitmap): ByteArray =
        Image.makeFromBitmap(bitmap).use { image ->
            checkNotNull(image.encodeToData(EncodedImageFormat.PNG)).use { it.bytes }
        }
}
