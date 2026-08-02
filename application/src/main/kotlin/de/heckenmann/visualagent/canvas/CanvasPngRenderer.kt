package de.heckenmann.visualagent.canvas

import de.heckenmann.visualagent.image.RgbaPngEncoder
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Renders toolkit-neutral canvas snapshots into PNG images.
 */
object CanvasPngRenderer {
    /**
     * Renders all snapshot figures into an ARGB PNG.
     *
     * @param snapshot Canvas snapshot to render
     * @param width Output width in pixels
     * @param height Output height in pixels
     * @return Encoded PNG bytes
     */
    fun render(
        snapshot: CanvasSnapshot,
        width: Int,
        height: Int,
    ): ByteArray {
        val pixels = IntArray(width * height) { 0xFF191A21.toInt() }
        snapshot.figures.forEach { figure ->
            when (figure.type) {
                "circle" -> fillEllipse(pixels, width, height, figure, figure.argbColor(0xFFFF79C6.toInt()))
                "line" -> drawLine(pixels, width, height, figure, figure.argbColor(0xFF8BE9FD.toInt()))
                "stroke" -> drawStroke(pixels, width, height, figure, figure.argbColor(0xFFF8F8F2.toInt()))
                "text" -> strokeRect(pixels, width, height, figure, figure.argbColor(0xFFFFB86C.toInt()))
                "image" -> strokeRect(pixels, width, height, figure, 0xFFBD93F9.toInt())
                else -> fillRect(pixels, width, height, figure, figure.argbColor(0xFF50FA7B.toInt()))
            }
        }
        return RgbaPngEncoder.encodeArgb(width, height, pixels)
    }

    private fun fillRect(
        pixels: IntArray,
        canvasWidth: Int,
        canvasHeight: Int,
        figure: CanvasFigureSnapshot,
        color: Int,
    ) {
        val left = figure.x.roundToInt().coerceIn(0, canvasWidth - 1)
        val top = figure.y.roundToInt().coerceIn(0, canvasHeight - 1)
        val right = (figure.x + figure.width).roundToInt().coerceIn(left, canvasWidth)
        val bottom = (figure.y + figure.height).roundToInt().coerceIn(top, canvasHeight)
        for (y in top until bottom) {
            for (x in left until right) pixels[y * canvasWidth + x] = color
        }
    }

    private fun strokeRect(
        pixels: IntArray,
        canvasWidth: Int,
        canvasHeight: Int,
        figure: CanvasFigureSnapshot,
        color: Int,
    ) {
        val left = figure.x.roundToInt().coerceIn(0, canvasWidth - 1)
        val top = figure.y.roundToInt().coerceIn(0, canvasHeight - 1)
        val right = (figure.x + figure.width).roundToInt().coerceIn(left, canvasWidth - 1)
        val bottom = (figure.y + figure.height).roundToInt().coerceIn(top, canvasHeight - 1)
        for (x in left..right) {
            pixels[top * canvasWidth + x] = color
            pixels[bottom * canvasWidth + x] = color
        }
        for (y in top..bottom) {
            pixels[y * canvasWidth + left] = color
            pixels[y * canvasWidth + right] = color
        }
    }

    private fun fillEllipse(
        pixels: IntArray,
        canvasWidth: Int,
        canvasHeight: Int,
        figure: CanvasFigureSnapshot,
        color: Int,
    ) {
        val left = figure.x.roundToInt().coerceIn(0, canvasWidth - 1)
        val top = figure.y.roundToInt().coerceIn(0, canvasHeight - 1)
        val right = (figure.x + figure.width).roundToInt().coerceIn(left, canvasWidth)
        val bottom = (figure.y + figure.height).roundToInt().coerceIn(top, canvasHeight)
        val radiusX = max(1.0, figure.width / 2.0)
        val radiusY = max(1.0, figure.height / 2.0)
        val centerX = figure.x + radiusX
        val centerY = figure.y + radiusY
        for (y in top until bottom) {
            for (x in left until right) {
                val normalizedX = (x - centerX) / radiusX
                val normalizedY = (y - centerY) / radiusY
                if (normalizedX * normalizedX + normalizedY * normalizedY <= 1.0) {
                    pixels[y * canvasWidth + x] = color
                }
            }
        }
    }

    private fun drawLine(
        pixels: IntArray,
        canvasWidth: Int,
        canvasHeight: Int,
        figure: CanvasFigureSnapshot,
        color: Int,
    ) {
        val x1 = figure.x.roundToInt()
        val y1 = figure.y.roundToInt()
        val x2 = (figure.x + figure.width).roundToInt()
        val y2 = (figure.y + figure.height).roundToInt()
        val steps = max(kotlin.math.abs(x2 - x1), kotlin.math.abs(y2 - y1)).coerceAtLeast(1)
        for (step in 0..steps) {
            val t = step.toDouble() / steps.toDouble()
            val x = (x1 + (x2 - x1) * t).roundToInt()
            val y = (y1 + (y2 - y1) * t).roundToInt()
            if (x in 0 until canvasWidth && y in 0 until canvasHeight) {
                pixels[y * canvasWidth + x] = color
            }
        }
    }

    private fun drawStroke(
        pixels: IntArray,
        canvasWidth: Int,
        canvasHeight: Int,
        figure: CanvasFigureSnapshot,
        color: Int,
    ) {
        val absolutePoints = figure.points.map { CanvasPoint(x = figure.x + it.x, y = figure.y + it.y) }
        absolutePoints.zipWithNext().forEach { (start, end) ->
            drawLineSegment(pixels, canvasWidth, canvasHeight, start, end, color)
        }
    }

    private fun drawLineSegment(
        pixels: IntArray,
        canvasWidth: Int,
        canvasHeight: Int,
        start: CanvasPoint,
        end: CanvasPoint,
        color: Int,
    ) {
        val x1 = start.x.roundToInt()
        val y1 = start.y.roundToInt()
        val x2 = end.x.roundToInt()
        val y2 = end.y.roundToInt()
        val steps = max(kotlin.math.abs(x2 - x1), kotlin.math.abs(y2 - y1)).coerceAtLeast(1)
        for (step in 0..steps) {
            val t = step.toDouble() / steps.toDouble()
            val x = (x1 + (x2 - x1) * t).roundToInt()
            val y = (y1 + (y2 - y1) * t).roundToInt()
            if (x in 0 until canvasWidth && y in 0 until canvasHeight) {
                pixels[y * canvasWidth + x] = color
            }
        }
    }
}

private fun CanvasFigureSnapshot.argbColor(defaultColor: Int): Int = parseHexColor(color) ?: defaultColor
