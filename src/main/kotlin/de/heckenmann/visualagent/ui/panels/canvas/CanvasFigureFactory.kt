package de.heckenmann.visualagent.ui.panels.canvas

import javafx.geometry.Point2D
import javafx.scene.image.Image
import org.jhotdraw8.css.value.CssColor
import org.jhotdraw8.css.value.CssSize
import org.jhotdraw8.draw.figure.EllipseFigure
import org.jhotdraw8.draw.figure.FillableFigure
import org.jhotdraw8.draw.figure.ImageFigure
import org.jhotdraw8.draw.figure.LineFigure
import org.jhotdraw8.draw.figure.PolylineFigure
import org.jhotdraw8.draw.figure.RectangleFigure
import org.jhotdraw8.draw.figure.StrokableFigure
import org.jhotdraw8.draw.figure.TextFigure
import java.io.File

/**
 * Builds editable JHotDraw figures from the canvas panel commands.
 */
internal object CanvasFigureFactory {
    fun text(
        text: String,
        x: Double,
        y: Double,
        color: String,
    ): TextFigure =
        TextFigure(x, y, text).apply {
            set(FillableFigure.FILL, CssColor.valueOf(color))
            set(StrokableFigure.STROKE, null)
        }

    fun rectangle(
        x: Double,
        y: Double,
        width: Double,
        height: Double,
        fillColor: String,
        strokeColor: String?,
    ): RectangleFigure =
        RectangleFigure(x, y, width, height).apply {
            set(FillableFigure.FILL, CssColor.valueOf(fillColor))
            set(StrokableFigure.STROKE, strokeColor?.let(CssColor::valueOf))
        }

    fun line(
        x1: Double,
        y1: Double,
        x2: Double,
        y2: Double,
        color: String,
        width: Double,
    ): LineFigure =
        LineFigure(x1, y1, x2, y2).apply {
            set(StrokableFigure.STROKE, CssColor.valueOf(color))
            set(StrokableFigure.STROKE_WIDTH, CssSize.of(width))
        }

    fun circle(
        centerX: Double,
        centerY: Double,
        radius: Double,
        fillColor: String,
    ): EllipseFigure =
        EllipseFigure(centerX - radius, centerY - radius, radius * 2, radius * 2).apply {
            set(FillableFigure.FILL, CssColor.valueOf(fillColor))
        }

    fun image(file: File): ImageFigure {
        val image = Image(file.toURI().toString(), false)
        require(!image.isError) { "Could not load image: ${file.name}" }
        val scale = minOf(1.0, MAX_INSERTED_IMAGE_SIZE / image.width, MAX_INSERTED_IMAGE_SIZE / image.height)
        return ImageFigure(40.0, 40.0, image.width * scale, image.height * scale).apply {
            set(ImageFigure.IMAGE_URI, file.toURI())
        }
    }

    fun stroke(points: List<Point2D>): PolylineFigure =
        PolylineFigure(*points.toTypedArray()).apply {
            set(StrokableFigure.STROKE, CssColor.valueOf(PEN_COLOR))
            set(StrokableFigure.STROKE_WIDTH, CssSize.of(PEN_WIDTH))
            set(FillableFigure.FILL, null)
        }
}
