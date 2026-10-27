package de.heckenmann.visualagent.canvas

import de.heckenmann.visualagent.error.CanvasOperationException
import de.heckenmann.visualagent.workspace.ImageHeaderReader
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.jetbrains.skia.shaper.Shaper

/** Renders bounded, server-owned canvas snapshots with Skia's AWT-free CPU raster API. */
object CanvasPngRenderer {
    /** Renders actual figure content; image bytes must come from an authorized managed-file resolver. */
    fun render(
        snapshot: CanvasSnapshot,
        width: Int,
        height: Int,
        imageBytes: (String) -> ByteArray = { throw IllegalArgumentException("No managed image resolver was supplied") },
    ): ByteArray {
        CanvasCaptureLimits.validateSize(width, height)
        CanvasCaptureLimits.validateGeometry(snapshot)
        return Surface.makeRasterN32Premul(width, height).use { surface ->
            surface.canvas.clear(0xFF191A21.toInt())
            snapshot.figures.forEach { drawFigure(surface.canvas, it, imageBytes) }
            surface.makeImageSnapshot().use { image ->
                checkNotNull(image.encodeToData(EncodedImageFormat.PNG)).use { it.bytes }
            }
        }
    }

    private fun drawFigure(
        canvas: Canvas,
        figure: CanvasFigureSnapshot,
        imageBytes: (String) -> ByteArray,
    ) {
        val bounds = Rect.makeXYWH(figure.x.toFloat(), figure.y.toFloat(), figure.width.toFloat(), figure.height.toFloat())
        Paint().use { paint ->
            paint.isAntiAlias = true
            paint.color = parseHexColor(figure.color) ?: defaultColor(figure.type)
            paint.strokeWidth = figure.strokeWidth.toFloat().coerceAtLeast(1f)
            when (figure.type) {
                "circle" -> canvas.drawOval(bounds, paint)
                "line" -> canvas.drawLine(bounds.left, bounds.top, bounds.right, bounds.bottom, paint)
                "stroke" ->
                    figure.points.zipWithNext().forEach { (start, end) ->
                        canvas.drawLine(
                            (figure.x + start.x).toFloat(),
                            (figure.y + start.y).toFloat(),
                            (figure.x + end.x).toFloat(),
                            (figure.y + end.y).toFloat(),
                            paint,
                        )
                    }
                "text" -> drawText(canvas, figure, bounds, paint)
                "image" -> drawImage(canvas, figure, bounds, imageBytes)
                else -> canvas.drawRect(bounds, paint)
            }
        }
    }

    private fun drawText(
        canvas: Canvas,
        figure: CanvasFigureSnapshot,
        bounds: Rect,
        paint: Paint,
    ) {
        // Skia supplies shaping and font fallback without introducing a desktop window or AWT.
        FontMgr.default.matchFamilyStyle(null, FontStyle.NORMAL).use { typeface ->
            Font(typeface, 16f).use { font ->
                Shaper.make().use { shaper ->
                    val saved = canvas.save()
                    try {
                        canvas.clipRect(bounds)
                        figure.content.lineSequence().take(2).forEachIndexed { index, line ->
                            shaper.shapeLine(line.take(16_384), font).use { text ->
                                canvas.drawTextLine(text, bounds.left, bounds.top + 18f + index * 20f, paint)
                            }
                        }
                    } finally {
                        canvas.restoreToCount(saved)
                    }
                }
            }
        }
    }

    private fun drawImage(
        canvas: Canvas,
        figure: CanvasFigureSnapshot,
        bounds: Rect,
        imageBytes: (String) -> ByteArray,
    ) {
        try {
            val bytes = imageBytes(figure.content)
            require(bytes.size <= CanvasCaptureLimits.MAX_IMAGE_BYTES) { "Canvas image exceeds the encoded byte limit" }
            val dimensions = ImageHeaderReader.dimensions(bytes)
            CanvasCaptureLimits.validateSize(dimensions.width, dimensions.height)
            Image.makeFromEncoded(bytes).use { image ->
                val scale = minOf(bounds.width / image.width, bounds.height / image.height)
                val width = image.width * scale
                val height = image.height * scale
                canvas.drawImageRect(
                    image,
                    Rect.makeXYWH(bounds.left + (bounds.width - width) / 2, bounds.top + (bounds.height - height) / 2, width, height),
                )
            }
        } catch (failure: Exception) {
            if (failure is CanvasOperationException) throw failure
            throw CanvasOperationException(
                summary = "Canvas image could not be captured",
                detail =
                    "Use a readable managed PNG, JPEG, or GIF image of at most 8 MiB, " +
                        "4096 pixels per dimension and 4194304 pixels in total.",
            )
        }
    }

    private fun defaultColor(type: String): Int =
        when (type) {
            "circle" -> 0xFFFF79C6.toInt()
            "line" -> 0xFF8BE9FD.toInt()
            "stroke" -> 0xFFF8F8F2.toInt()
            "text" -> 0xFFFFB86C.toInt()
            else -> 0xFF50FA7B.toInt()
        }
}
