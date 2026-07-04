@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.canvas.CanvasFigureSnapshot
import de.heckenmann.visualagent.canvas.parseHexColor
import org.jetbrains.skia.Image as SkiaImage

/**
 * Renders a single canvas figure.
 *
 * Selected figures are indicated by a thin accent border; the figure keeps its
 * natural background so surrounding figures remain visible.
 *
 * @param figure Figure snapshot to render
 * @param selected True when this figure is the currently selected one
 * @param imageBytesForPath Resolver for imported image bytes
 * @param onResize Callback invoked from the resize handle when selected
 */
@Composable
internal fun FigureNode(
    figure: CanvasFigureSnapshot,
    selected: Boolean,
    imageBytesForPath: (String) -> ByteArray?,
    onResize: (Double, Double) -> Unit,
) {
    val shape: Shape = if (figure.type == "circle") CircleShape else RoundedCornerShape(8.dp)
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(figureColor(figure))
                .border(if (selected) 1.5.dp else 0.dp, if (selected) Color(0xFF8BE9FD) else Color.Transparent, shape)
                .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (figure.type) {
            "text" ->
                Text(
                    text = figure.content.ifBlank { "Text" },
                    color = Color(0xFF191A21),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            "stroke" -> StrokeFigure(figure)
            "image" -> ImageFigure(figure, imageBytesForPath)
        }
        if (selected) {
            FigureResizeHandle(
                initialWidth = figure.width,
                initialHeight = figure.height,
                onResize = onResize,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
    }
}

@Composable
internal fun FigureResizeHandle(
    initialWidth: Double,
    initialHeight: Double,
    onResize: (Double, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    var width by remember(initialWidth) { mutableStateOf(initialWidth) }
    var height by remember(initialHeight) { mutableStateOf(initialHeight) }
    Box(
        modifier =
            modifier
                .size(12.dp)
                .background(Color(0xFFFF79C6), RoundedCornerShape(4.dp))
                .pointerInput(initialWidth, initialHeight) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        width += dragAmount.x.toDouble()
                        height += dragAmount.y.toDouble()
                        onResize(width, height)
                    }
                },
    )
}

@Composable
internal fun StrokeFigure(figure: CanvasFigureSnapshot) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        figure.points.zipWithNext().forEach { (start, end) ->
            drawLine(
                color = figure.color.toComposeColor(Color(0xFFF8F8F2)),
                start =
                    androidx.compose.ui.geometry.Offset(
                        start.x.toFloat(),
                        start.y.toFloat(),
                    ),
                end =
                    androidx.compose.ui.geometry.Offset(
                        end.x.toFloat(),
                        end.y.toFloat(),
                    ),
                strokeWidth = figure.strokeWidth.toFloat().coerceAtLeast(1f),
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
internal fun ImageFigure(
    figure: CanvasFigureSnapshot,
    imageBytesForPath: (String) -> ByteArray?,
) {
    val imageBitmap =
        remember(figure.content) {
            imageBytesForPath(figure.content)
                ?.let { bytes -> runCatching { SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull() }
        }
    if (imageBitmap == null) {
        Text(
            text = "Image",
            color = Color(0xFF191A21),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    } else {
        Image(bitmap = imageBitmap, contentDescription = null, modifier = Modifier.fillMaxSize())
    }
}

private fun figureColor(figure: CanvasFigureSnapshot): Color =
    when (figure.type) {
        "circle" -> figure.color.toComposeColor(Color(0xFFFF79C6))
        "line" -> figure.color.toComposeColor(Color(0xFF8BE9FD))
        "stroke" -> Color.Transparent
        "text" -> figure.color.toComposeColor(Color(0xFFFFB86C))
        "image" -> Color(0xFFBD93F9)
        else -> figure.color.toComposeColor(Color(0xFF50FA7B))
    }

internal fun String.toComposeColor(defaultColor: Color): Color {
    val argb = parseHexColor(this) ?: return defaultColor
    return Color(argb)
}
