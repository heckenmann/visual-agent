package de.heckenmann.visualagent.ui.panels.canvas

import org.jhotdraw8.draw.figure.EllipseFigure
import org.jhotdraw8.draw.figure.Figure
import org.jhotdraw8.draw.figure.ImageFigure
import org.jhotdraw8.draw.figure.LineFigure
import org.jhotdraw8.draw.figure.PolylineFigure
import org.jhotdraw8.draw.figure.RectangleFigure
import org.jhotdraw8.draw.figure.TextFigure

internal fun Figure.toCanvasSnapshot(index: Int): CanvasFigureSnapshot {
    val bounds = layoutBounds
    return CanvasFigureSnapshot(
        index = index,
        type = canvasFigureType(),
        x = bounds.minX,
        y = bounds.minY,
        width = bounds.width,
        height = bounds.height,
    )
}

private fun Figure.canvasFigureType(): String =
    when (this) {
        is TextFigure -> "text"
        is RectangleFigure -> "rectangle"
        is LineFigure -> "line"
        is EllipseFigure -> "circle"
        is ImageFigure -> "image"
        is PolylineFigure -> "stroke"
        else -> javaClass.simpleName.removeSuffix("Figure").lowercase()
    }
