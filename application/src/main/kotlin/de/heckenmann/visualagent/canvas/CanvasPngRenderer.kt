package de.heckenmann.visualagent.canvas

import org.jetbrains.skia.Canvas
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface

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
                "text" -> CanvasTextRasterizer.draw(canvas, figure.content, bounds, paint.color)
                "image" -> CanvasImageRasterizer.draw(canvas, figure.content, bounds, imageBytes)
                else -> canvas.drawRect(bounds, paint)
            }
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
