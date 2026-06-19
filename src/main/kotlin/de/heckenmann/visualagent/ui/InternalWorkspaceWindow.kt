package de.heckenmann.visualagent.ui

import javafx.geometry.Pos
import javafx.scene.Cursor
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import org.kordamp.ikonli.javafx.FontIcon
import kotlin.math.max
import kotlin.math.min

/**
 * Draggable and resizable in-window container for primary workspace panels.
 *
 * The window keeps the hosted panel in the scene graph permanently, while navigation
 * can bring it to the front and mark it as active.
 */
internal class InternalWorkspaceWindow(
    title: String,
    iconLiteral: String,
    content: Node,
) : BorderPane() {
    private var dragStartSceneX = 0.0
    private var dragStartSceneY = 0.0
    private var dragStartLayoutX = 0.0
    private var dragStartLayoutY = 0.0
    private var resizeStartSceneX = 0.0
    private var resizeStartSceneY = 0.0
    private var resizeStartWidth = 0.0
    private var resizeStartHeight = 0.0

    init {
        styleClass.add("workspace-window")
        minWidth = MIN_WIDTH
        minHeight = MIN_HEIGHT

        top = windowHeader(title, iconLiteral)
        center = content
        bottom = resizeHandle()
        setOnMousePressed { toFront() }
    }

    fun setActive(active: Boolean) {
        styleClass.remove("workspace-window-active")
        if (active) {
            styleClass.add("workspace-window-active")
        }
    }

    fun place(
        x: Double,
        y: Double,
        width: Double,
        height: Double,
    ) {
        layoutX = x
        layoutY = y
        prefWidth = width
        prefHeight = height
    }

    fun keepInside(
        desktopWidth: Double,
        desktopHeight: Double,
    ) {
        val currentWidth = if (width > 0.0) width else prefWidth
        val currentHeight = if (height > 0.0) height else prefHeight
        layoutX = clamp(layoutX, 0.0, max(0.0, desktopWidth - currentWidth))
        layoutY = clamp(layoutY, 0.0, max(0.0, desktopHeight - currentHeight))
    }

    private fun windowHeader(
        title: String,
        iconLiteral: String,
    ): HBox {
        val header = HBox(8.0)
        header.alignment = Pos.CENTER_LEFT
        header.styleClass.add("workspace-window-header")
        header.children.addAll(
            FontIcon(iconLiteral).apply {
                iconSize = 12
                styleClass.add("workspace-window-icon")
            },
            Label(title).apply { styleClass.add("workspace-window-title") },
            Region().apply { HBox.setHgrow(this, javafx.scene.layout.Priority.ALWAYS) },
            closeButton(),
            FontIcon("fas-grip-lines").apply {
                iconSize = 10
                styleClass.add("workspace-window-grip")
            },
        )
        header.cursor = Cursor.MOVE
        header.setOnMousePressed { event ->
            toFront()
            dragStartSceneX = event.sceneX
            dragStartSceneY = event.sceneY
            dragStartLayoutX = layoutX
            dragStartLayoutY = layoutY
            event.consume()
        }
        header.setOnMouseDragged { event ->
            val parentPane = parent as? Pane
            val maxX = (parentPane?.width ?: Double.MAX_VALUE) - width
            val maxY = (parentPane?.height ?: Double.MAX_VALUE) - height
            layoutX = clamp(dragStartLayoutX + event.sceneX - dragStartSceneX, 0.0, max(0.0, maxX))
            layoutY = clamp(dragStartLayoutY + event.sceneY - dragStartSceneY, 0.0, max(0.0, maxY))
            event.consume()
        }
        return header
    }

    private fun closeButton(): Button =
        Button().apply {
            graphic =
                FontIcon("fas-times").apply {
                    iconSize = 10
                    styleClass.add("workspace-window-close-icon")
                }
            styleClass.add("workspace-window-close")
            isFocusTraversable = false
            setOnAction {
                this@InternalWorkspaceWindow.isVisible = false
                this@InternalWorkspaceWindow.isManaged = false
            }
        }

    private fun resizeHandle(): StackPane {
        val resizeIcon =
            FontIcon("fas-grip-lines").apply {
                iconSize = 10
                styleClass.add("workspace-window-resize-icon")
            }
        val handle = StackPane(resizeIcon)
        handle.alignment = Pos.CENTER_RIGHT
        handle.cursor = Cursor.SE_RESIZE
        handle.styleClass.add("workspace-window-resize")
        handle.setOnMousePressed { event ->
            toFront()
            resizeStartSceneX = event.sceneX
            resizeStartSceneY = event.sceneY
            resizeStartWidth = width
            resizeStartHeight = height
            event.consume()
        }
        handle.setOnMouseDragged { event ->
            val parentPane = parent as? Pane
            val maxWidth = max(MIN_WIDTH, (parentPane?.width ?: Double.MAX_VALUE) - layoutX)
            val maxHeight = max(MIN_HEIGHT, (parentPane?.height ?: Double.MAX_VALUE) - layoutY)
            prefWidth = clamp(resizeStartWidth + event.sceneX - resizeStartSceneX, MIN_WIDTH, maxWidth)
            prefHeight = clamp(resizeStartHeight + event.sceneY - resizeStartSceneY, MIN_HEIGHT, maxHeight)
            event.consume()
        }
        return handle
    }

    private fun clamp(
        value: Double,
        minValue: Double,
        maxValue: Double,
    ): Double = min(max(value, minValue), maxValue)

    private companion object {
        const val MIN_WIDTH = 360.0
        const val MIN_HEIGHT = 260.0
    }
}
