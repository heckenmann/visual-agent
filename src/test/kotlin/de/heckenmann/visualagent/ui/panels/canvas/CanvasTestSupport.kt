package de.heckenmann.visualagent.ui.panels.canvas

import de.heckenmann.visualagent.knowledge.PreferenceStore
import javafx.event.EventType
import javafx.geometry.Bounds
import javafx.geometry.Point2D
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.input.PickResult
import org.jhotdraw8.draw.SimpleDrawingView
import org.jhotdraw8.draw.figure.Figure
import org.jhotdraw8.draw.tool.SelectionTool

internal fun CanvasPanel.figures(): List<Figure> {
    val drawingView = field<SimpleDrawingView>("drawingView")
    return requireNotNull(drawingView.drawing).children.flatMap { it.children }
}

internal val Bounds.center: Point2D
    get() = Point2D(centerX, centerY)

internal fun descendants(root: Parent): List<Node> =
    root.childrenUnmodifiable.flatMap { child ->
        listOf(child) + if (child is Parent) descendants(child) else emptyList()
    }

internal fun mouseEvent(
    target: Node,
    type: EventType<MouseEvent>,
    x: Double,
    y: Double,
    primaryDown: Boolean = true,
): MouseEvent {
    val scenePoint = target.localToScene(x, y)
    return MouseEvent(
        type,
        scenePoint.x,
        scenePoint.y,
        scenePoint.x,
        scenePoint.y,
        MouseButton.PRIMARY,
        1,
        false,
        false,
        false,
        false,
        primaryDown,
        false,
        false,
        false,
        false,
        false,
        PickResult(target, x, y),
    )
}

internal fun sceneMouseEvent(
    target: Node,
    type: EventType<MouseEvent>,
    sceneX: Double,
    sceneY: Double,
    primaryDown: Boolean = true,
) = MouseEvent(
    type,
    sceneX,
    sceneY,
    sceneX,
    sceneY,
    MouseButton.PRIMARY,
    1,
    false,
    false,
    false,
    false,
    primaryDown,
    false,
    false,
    false,
    false,
    false,
    PickResult(target, sceneX, sceneY),
)

@Suppress("UNCHECKED_CAST")
internal fun <T> CanvasPanel.field(name: String): T {
    val field = CanvasPanel::class.java.getDeclaredField(name)
    field.isAccessible = true
    return field.get(this) as T
}

@Suppress("UNCHECKED_CAST")
internal fun <T> CanvasToolbar.field(name: String): T {
    val field = CanvasToolbar::class.java.getDeclaredField(name)
    field.isAccessible = true
    return field.get(this) as T
}

@Suppress("UNCHECKED_CAST")
internal fun <T> SelectionTool.field(name: String): T {
    val field = SelectionTool::class.java.getDeclaredField(name)
    field.isAccessible = true
    return field.get(this) as T
}

internal class InMemoryPreferenceStore : PreferenceStore {
    private val values = mutableMapOf<String, String>()

    override fun getPreference(key: String): String? = values[key]

    override fun setPreference(
        key: String,
        value: String,
    ) {
        values[key] = value
    }
}
