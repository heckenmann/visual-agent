package de.heckenmann.visualagent.canvas

import de.heckenmann.visualagent.error.CanvasOperationException
import de.heckenmann.visualagent.image.RgbaPngEncoder
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CanvasCaptureContentTest {
    @Test
    fun `imported image pixels appear at the requested position and scale`() {
        val red = 0xFFFF0000.toInt()
        val blue = 0xFF0000FF.toInt()
        val fixture = RgbaPngEncoder.encodeArgb(2, 1, intArrayOf(red, blue))
        val snapshot = snapshot("image", "imports/fixture.png")
        val bytes =
            CanvasPngRenderer.render(snapshot, 100, 80) { path ->
                assertEquals("imports/fixture.png", path)
                fixture
            }
        pixels(bytes) { image ->
            assertEquals(red, image.getColor(12, 30))
            assertEquals(blue, image.getColor(47, 30))
            assertEquals(BACKGROUND, image.getColor(5, 5))
        }
    }

    @Test
    fun `text capture renders actual glyphs instead of a rectangle border`() {
        val blank = CanvasPngRenderer.render(snapshot("text", ""), 100, 80)
        val text = CanvasPngRenderer.render(snapshot("text", "Hello"), 100, 80)
        pixels(blank) { image -> assertEquals(BACKGROUND, image.getColor(10, 10)) }
        pixels(text) { image ->
            val ink = (12..46).sumOf { x -> (13..26).count { y -> image.getColor(x, y) != BACKGROUND } }
            assertTrue(ink > 20, "Glyph interiors must contain text pixels")
            assertEquals(BACKGROUND, image.getColor(10, 10), "The old rectangle outline must be absent")
        }
    }

    @Test
    fun `image decoder rejects excessive declared dimensions and unreadable content`() {
        val oversizedHeader =
            RgbaPngEncoder.encodeArgb(1, 1, intArrayOf(-1)).also { bytes ->
                bytes[16] = 0x7f
            }
        assertFailsWith<CanvasOperationException> { CanvasPngRenderer.render(snapshot("image", "fixture"), 100, 80) { oversizedHeader } }
        assertFailsWith<CanvasOperationException> { CanvasPngRenderer.render(snapshot("image", "fixture"), 100, 80) { byteArrayOf(1, 2) } }
        assertFailsWith<CanvasOperationException> { CanvasPngRenderer.render(snapshot("image", "fixture"), 100, 80) }
    }

    @Test
    fun `captured PNG remains immutable after source pixels change`() {
        var fixture = RgbaPngEncoder.encodeArgb(1, 1, intArrayOf(0xFFFF0000.toInt()))
        val original = CanvasPngRenderer.render(snapshot("image", "fixture"), 100, 80) { fixture }
        fixture = RgbaPngEncoder.encodeArgb(1, 1, intArrayOf(0xFF0000FF.toInt()))
        val updated = CanvasPngRenderer.render(snapshot("image", "fixture"), 100, 80) { fixture }
        pixels(original) { assertEquals(0xFFFF0000.toInt(), it.getColor(30, 30)) }
        pixels(updated) { assertEquals(0xFF0000FF.toInt(), it.getColor(30, 30)) }
    }

    private fun snapshot(
        type: String,
        content: String,
    ) = CanvasSnapshot(
        figureCount = 1,
        zoomPercent = 100,
        gridVisible = true,
        figures = listOf(CanvasFigureSnapshot(0, type, 10.0, 10.0, 40.0, 40.0, content, "#FFFFFFFF")),
    )

    private fun pixels(
        bytes: ByteArray,
        assertPixels: (Bitmap) -> Unit,
    ) {
        Image.makeFromEncoded(bytes).use { image -> Bitmap.makeFromImage(image).use(assertPixels) }
    }

    private companion object {
        const val BACKGROUND = 0xFF191A21.toInt()
    }
}
