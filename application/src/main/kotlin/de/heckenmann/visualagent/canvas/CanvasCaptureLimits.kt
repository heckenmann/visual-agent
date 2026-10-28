package de.heckenmann.visualagent.canvas

import de.heckenmann.visualagent.error.CanvasOperationException
import kotlin.math.ceil
import kotlin.math.max

/** Validates capture geometry before any raster buffer or image decoder is allocated. */
internal object CanvasCaptureLimits {
    const val MAX_DIMENSION = 4096
    const val MAX_PIXELS = 4_194_304L
    const val MAX_IMAGE_BYTES = 8L * 1024 * 1024

    fun dimensions(snapshot: CanvasSnapshot): Pair<Int, Int> {
        validateGeometry(snapshot)
        val width = max(800.0, snapshot.figures.maxOfOrNull { ceil(it.x + it.width + 40) } ?: 800.0)
        val height = max(600.0, snapshot.figures.maxOfOrNull { ceil(it.y + it.height + 40) } ?: 600.0)
        checkLimit(width <= MAX_DIMENSION && height <= MAX_DIMENSION)
        validateSize(width.toInt(), height.toInt())
        return width.toInt() to height.toInt()
    }

    fun validateSize(
        width: Int,
        height: Int,
    ) {
        checkLimit(width in 1..MAX_DIMENSION && height in 1..MAX_DIMENSION)
        checkLimit(width.toLong() * height.toLong() <= MAX_PIXELS)
    }

    fun validateGeometry(snapshot: CanvasSnapshot) {
        snapshot.figures.forEach { figure ->
            val values =
                listOf(
                    figure.x,
                    figure.y,
                    figure.width,
                    figure.height,
                    figure.strokeWidth,
                    figure.x + figure.width,
                    figure.y + figure.height,
                )
            checkLimit(values.all { it.isFinite() && it.toFloat().isFinite() })
            checkLimit(figure.width >= 0 && figure.height >= 0 && figure.strokeWidth >= 0)
            checkLimit(
                figure.points.all {
                    it.x.isFinite() &&
                        it.y.isFinite() &&
                        (figure.x + it.x).toFloat().isFinite() &&
                        (figure.y + it.y).toFloat().isFinite()
                },
            )
        }
    }

    private fun checkLimit(valid: Boolean) {
        if (!valid) {
            throw CanvasOperationException(
                summary = "Canvas capture exceeds safe bounds",
                detail =
                    "Capture supports finite geometry, at most $MAX_DIMENSION pixels per dimension and $MAX_PIXELS pixels in total. " +
                        "Move or resize figures into the capture area and try again; the editable canvas is unchanged.",
            )
        }
    }
}
