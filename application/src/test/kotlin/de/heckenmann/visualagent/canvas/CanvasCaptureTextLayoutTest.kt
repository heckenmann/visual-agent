package de.heckenmann.visualagent.canvas

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CanvasCaptureTextLayoutTest {
    @Test
    fun `wraps text into a second visible line when a figure is narrow`() {
        pixels(capture("alpha beta gamma delta", 75.0, 60.0)) { image ->
            assertTrue(ink(image, 10..84, 10..28) > 20)
            assertTrue(ink(image, 10..84, 30..50) > 20, "Wrapped words must appear on the second line")
            assertEquals(0, ink(image, 85..109, 10..70), "Text must remain inside its figure")
        }
    }

    @Test
    fun `explicit newlines retain two lines and ellipsize further content`() {
        pixels(capture("First\nSecond\nThird", 85.0, 90.0)) { image ->
            assertTrue(ink(image, 10..94, 10..28) > 20)
            assertTrue(ink(image, 10..94, 30..50) > 20)
            assertEquals(0, ink(image, 10..94, 55..100), "The editor and capture both show at most two lines")
        }
    }

    @Test
    fun `text is clipped to the figure height and empty figures remain safe`() {
        pixels(capture("First\nSecond", 85.0, 20.0)) { image ->
            assertTrue(ink(image, 10..94, 10..28) > 20)
            assertEquals(0, ink(image, 10..94, 30..60))
        }
        pixels(capture("text", 0.0, 0.0)) { image ->
            assertEquals(0, ink(image, 0..109, 0..109))
        }
    }

    private fun capture(
        text: String,
        width: Double,
        height: Double,
    ) = CanvasPngRenderer.render(
        CanvasSnapshot(1, 100, true, figures = listOf(CanvasFigureSnapshot(0, "text", 10.0, 10.0, width, height, text, "#FFFFFFFF"))),
        110,
        110,
    )

    private fun ink(
        image: Bitmap,
        columns: IntRange,
        rows: IntRange,
    ): Int = columns.sumOf { x -> rows.count { y -> image.getColor(x, y) != BACKGROUND } }

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
