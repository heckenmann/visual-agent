package de.heckenmann.visualagent.ui.panels.canvas

import de.heckenmann.visualagent.ui.panels.FxTestSupport
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.input.MouseEvent
import javafx.scene.layout.StackPane
import org.jhotdraw8.draw.SimpleDrawingEditor
import org.jhotdraw8.draw.SimpleDrawingView
import org.jhotdraw8.draw.constrain.GridConstrainer
import org.jhotdraw8.draw.figure.EllipseFigure
import org.jhotdraw8.draw.figure.ImageFigure
import org.jhotdraw8.draw.figure.LineFigure
import org.jhotdraw8.draw.figure.PolylineFigure
import org.jhotdraw8.draw.figure.RectangleFigure
import org.jhotdraw8.draw.figure.TextFigure
import org.jhotdraw8.draw.tool.SelectionTool
import org.jhotdraw8.draw.tool.SimpleDragTracker
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CanvasPanelInteractionTest {
    private val preferenceStore = InMemoryPreferenceStore()

    @Test
    fun `programmatic drawing creates editable jhotdraw figures`() =
        FxTestSupport.run {
            val panel = panel()
            panel.drawText("Hello", 20.0, 30.0)
            panel.drawRect(40.0, 50.0, 80.0, 40.0, "#ffffff", "#000000")
            panel.drawLine(0.0, 0.0, 100.0, 100.0)
            panel.drawCircle(120.0, 120.0, 15.0, "#00ff00")

            val figures = panel.figures()

            assertEquals(4, figures.size)
            assertIs<TextFigure>(figures[0])
            assertIs<RectangleFigure>(figures[1])
            assertIs<LineFigure>(figures[2])
            assertIs<EllipseFigure>(figures[3])
            Unit
        }

    @Test
    fun `canvas snapshot exposes model readable figure summaries`() =
        FxTestSupport.run {
            val panel = panel()
            panel.drawText("Hello", 20.0, 30.0)
            panel.drawRect(40.0, 50.0, 80.0, 40.0, "#ffffff", "#000000")

            val snapshot = panel.snapshot()

            assertEquals(2, snapshot.figureCount)
            assertEquals(100, snapshot.zoomPercent)
            assertTrue(snapshot.gridVisible)
            assertEquals(listOf("text", "rectangle"), snapshot.figures.map { it.type })
        }

    @Test
    fun `jhotdraw undo redo and zoom remain wired to toolbar`() =
        FxTestSupport.run {
            val panel = panel()
            val drawingView = panel.field<SimpleDrawingView>("drawingView")
            val toolbar = panel.field<CanvasToolbar>("toolbar")
            val buttons = descendants(toolbar).filterIsInstance<Button>()
            panel.drawRect(20.0, 20.0, 40.0, 30.0, "#ff0000")

            val undo = buttons.first { it.text == "Undo" }
            val redo = buttons.first { it.text == "Redo" }
            assertFalse(undo.isDisable)
            undo.fire()
            assertTrue(panel.figures().isEmpty())
            assertFalse(redo.isDisable)
            redo.fire()
            assertEquals(1, panel.figures().size)

            buttons.first { it.text == "In" }.fire()
            assertEquals(1.1, drawingView.zoomFactor, 0.001)
            buttons.first { it.text == "110%" }.fire()
            assertEquals(1.0, drawingView.zoomFactor, 0.001)
        }

    @Test
    fun `grid and tool controls configure jhotdraw editor`() =
        FxTestSupport.run {
            val panel = panel()
            val toolbar = panel.field<CanvasToolbar>("toolbar")
            val editor = panel.field<SimpleDrawingEditor>("drawingEditor")
            val grid = panel.field<GridConstrainer>("gridConstrainer")
            val buttons = descendants(toolbar).filterIsInstance<Button>()

            buttons.first { it.text == "Pen" }.fire()
            assertTrue(buttons.first { it.text == "Pen" }.styleClass.contains("active"))
            assertEquals(null, editor.activeTool)

            buttons.first { it.text == "Select" }.fire()
            assertTrue(buttons.first { it.text == "Select" }.styleClass.contains("active"))
            assertTrue(editor.activeTool != null)

            descendants(toolbar).filterIsInstance<CheckBox>().single().fire()
            assertFalse(grid.drawGridProperty().get())
        }

    @Test
    fun `pen gesture commits a jhotdraw polyline and clear is undoable`() =
        FxTestSupport.run {
            val panel = panel()
            Scene(panel, 800.0, 600.0)
            panel.applyCss()
            panel.layout()
            val toolbar = panel.field<CanvasToolbar>("toolbar")
            val viewport = panel.field<StackPane>("editorViewport")
            descendants(toolbar).filterIsInstance<Button>().first { it.text == "Pen" }.fire()

            viewport.fireEvent(mouseEvent(viewport, MouseEvent.MOUSE_PRESSED, 40.0, 80.0))
            viewport.fireEvent(mouseEvent(viewport, MouseEvent.MOUSE_DRAGGED, 80.0, 100.0))
            viewport.fireEvent(mouseEvent(viewport, MouseEvent.MOUSE_RELEASED, 80.0, 100.0))

            assertIs<PolylineFigure>(panel.figures().single())
            panel.clearCanvas()
            assertTrue(panel.figures().isEmpty())
            descendants(toolbar).filterIsInstance<Button>().first { it.text == "Undo" }.fire()
            assertEquals(1, panel.figures().size)
        }

    @Test
    fun `window resize preserves drawing model and figure identity`() =
        FxTestSupport.run {
            val panel = panel()
            panel.resize(700.0, 500.0)
            panel.layout()
            panel.drawRect(24.0, 24.0, 20.0, 20.0, "#ff0000")
            val drawingView = panel.field<SimpleDrawingView>("drawingView")
            val figure = panel.figures().single()

            listOf(920.0 to 680.0, 480.0 to 340.0, 810.0 to 610.0).forEach { (width, height) ->
                panel.resize(width, height)
                panel.layout()
                assertSame(figure, panel.figures().single())
                assertSame(drawingView, panel.field("drawingView"))
            }
        }

    @Test
    fun `image import creates selected resizable image figure`() =
        FxTestSupport.run {
            val file = Files.createTempFile("visual-agent-canvas", ".png").toFile()
            try {
                ImageIO.write(BufferedImage(800, 400, BufferedImage.TYPE_INT_ARGB), "png", file)
                val panel = panel()

                panel.addImage(file)

                val figure = assertIs<ImageFigure>(panel.figures().single())
                val drawingView = panel.field<SimpleDrawingView>("drawingView")
                assertEquals(480.0, figure.layoutBounds.width, 0.001)
                assertTrue(drawingView.selectedFigures.contains(figure))
            } finally {
                file.delete()
            }
        }

    @Test
    fun `selection tool moves and resizes an imported image`() =
        FxTestSupport.run {
            val file = Files.createTempFile("visual-agent-editable-image", ".png").toFile()
            try {
                ImageIO.write(BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB), "png", file)
                val panel = panel()
                Scene(panel, 800.0, 600.0)
                panel.resize(800.0, 600.0)
                panel.addImage(file)
                panel.applyCss()
                panel.layout()
                val drawingView = panel.field<SimpleDrawingView>("drawingView")
                drawingView.model.validate(drawingView)
                drawingView.recreateHandles()
                panel.layout()
                val figure = assertIs<ImageFigure>(panel.figures().single())
                val eventPane = descendants(drawingView.node as Parent).first { it.id == "toolEventPane" }

                val center = drawingView.worldToView(figure.layoutBoundsInWorld.center)
                assertSame(figure, drawingView.findFigure(center.x, center.y))
                assertTrue(drawingView.selectedFigures.contains(figure))
                assertSame(figure, drawingView.findFigure(center.x, center.y, drawingView.selectedFigures))
                val eventCenter = eventPane.sceneToLocal(drawingView.node.localToScene(center))
                assertEquals(center, eventCenter)
                val centerInScene = drawingView.node.localToScene(center)
                eventPane.fireEvent(sceneMouseEvent(eventPane, MouseEvent.MOUSE_PRESSED, centerInScene.x, centerInScene.y))
                val selectionTool = panel.field<SelectionTool>("selectionTool")
                assertSame(drawingView, selectionTool.drawingView)
                assertIs<SimpleDragTracker>(selectionTool.field("tracker"))
                eventPane.fireEvent(
                    sceneMouseEvent(eventPane, MouseEvent.MOUSE_DRAGGED, centerInScene.x + 50.0, centerInScene.y + 30.0),
                )
                eventPane.fireEvent(
                    sceneMouseEvent(
                        eventPane,
                        MouseEvent.MOUSE_RELEASED,
                        centerInScene.x + 50.0,
                        centerInScene.y + 30.0,
                        primaryDown = false,
                    ),
                )
                drawingView.model.validate(drawingView)
                assertEquals(90.0, figure.layoutBoundsInWorld.minX, 0.001)
                assertEquals(70.0, figure.layoutBoundsInWorld.minY, 0.001)

                drawingView.recreateHandles()
                panel.layout()
                val bottomRight = drawingView.worldToView(figure.layoutBoundsInWorld.maxX, figure.layoutBoundsInWorld.maxY)
                val bottomRightInScene = drawingView.node.localToScene(bottomRight)
                eventPane.fireEvent(
                    sceneMouseEvent(eventPane, MouseEvent.MOUSE_PRESSED, bottomRightInScene.x, bottomRightInScene.y),
                )
                eventPane.fireEvent(
                    sceneMouseEvent(
                        eventPane,
                        MouseEvent.MOUSE_DRAGGED,
                        bottomRightInScene.x + 40.0,
                        bottomRightInScene.y + 20.0,
                    ),
                )
                eventPane.fireEvent(
                    sceneMouseEvent(
                        eventPane,
                        MouseEvent.MOUSE_RELEASED,
                        bottomRightInScene.x + 40.0,
                        bottomRightInScene.y + 20.0,
                        primaryDown = false,
                    ),
                )
                drawingView.model.validate(drawingView)
                assertTrue(figure.layoutBoundsInWorld.width > 200.0)
                assertTrue(figure.layoutBoundsInWorld.height > 100.0)
            } finally {
                file.delete()
            }
        }

    @Test
    fun `canvas document survives panel recreation`() =
        FxTestSupport.run {
            val firstPanel = panel()
            firstPanel.drawRect(15.0, 25.0, 80.0, 40.0, "#ff0000")
            firstPanel.field<CanvasDocumentPersistence>("persistence").save(
                firstPanel.field<org.jhotdraw8.draw.figure.SimpleLayeredDrawing>("activeDrawing"),
            )

            val restoredPanel = panel()

            val figure = assertIs<RectangleFigure>(restoredPanel.figures().single())
            assertEquals(15.0, figure.layoutBounds.minX, 0.001)
            assertEquals(80.0, figure.layoutBounds.width, 0.001)
        }

    @Test
    fun `toolbar callbacks and state are deterministic`() =
        FxTestSupport.run {
            val actions = mutableListOf<String>()
            val toolbar =
                CanvasToolbar(
                    onSelect = { actions += "select" },
                    onPen = { actions += "pen" },
                    onInsertImage = { actions += "image" },
                    onDeleteSelection = { actions += "delete" },
                    onUndo = { actions += "undo" },
                    onRedo = { actions += "redo" },
                    onZoomOut = { actions += "out" },
                    onZoomReset = { actions += "reset" },
                    onZoomIn = { actions += "in" },
                    onGridChanged = { actions += "grid:$it" },
                    onClear = { actions += "clear" },
                    onExport = { actions += "export" },
                )
            toolbar.updateHistoryActions(canUndo = false, canRedo = true)
            toolbar.updateZoom(125)
            toolbar.selectTool(CanvasTool.PEN)

            val buttons = descendants(toolbar).filterIsInstance<Button>()
            assertTrue(buttons.first { it.text == "Undo" }.isDisable)
            assertFalse(buttons.first { it.text == "Redo" }.isDisable)
            assertTrue(buttons.first { it.text == "Delete" }.isDisable)
            toolbar.updateSelectionActions(hasDeletableSelection = true)
            assertFalse(buttons.first { it.text == "Delete" }.isDisable)
            assertEquals("125%", buttons.first { it.text == "125%" }.text)
            buttons.filterNot(Button::isDisable).forEach(Button::fire)
            descendants(toolbar).filterIsInstance<CheckBox>().single().fire()

            assertTrue(actions.containsAll(listOf("select", "pen", "image", "delete", "redo", "out", "reset", "in", "clear", "export")))
            assertTrue(actions.any { it.startsWith("grid:") })
        }

    private fun panel() = CanvasPanel(preferenceStore)

    companion object {
        @JvmStatic
        @BeforeAll
        fun startToolkit() = FxTestSupport.startToolkit()
    }
}
