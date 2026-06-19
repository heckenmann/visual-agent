package de.heckenmann.visualagent.ui.panels.canvas

import de.heckenmann.visualagent.ui.panels.FxTestSupport
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.layout.StackPane
import org.jhotdraw8.draw.SimpleDrawingView
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CanvasSelectionDeletionTest {
    private val preferenceStore = InMemoryPreferenceStore()

    @Test
    fun `delete key removes selected canvas figure and remains undoable`() =
        FxTestSupport.run {
            val panel = panel()
            Scene(panel, 800.0, 600.0)
            val drawingView = panel.field<SimpleDrawingView>("drawingView")
            val viewport = panel.field<StackPane>("editorViewport")
            panel.drawRect(20.0, 20.0, 40.0, 30.0, "#ff0000")
            drawingView.selectedFigures.add(panel.figures().single())

            viewport.fireEvent(keyPressed(KeyCode.DELETE))

            assertTrue(panel.figures().isEmpty())
            val toolbarButtons = descendants(panel.field<CanvasToolbar>("toolbar")).filterIsInstance<Button>()
            toolbarButtons.first { it.text == "Undo" }.fire()
            assertEquals(1, panel.figures().size)
        }

    @Test
    fun `backspace key removes selected canvas figure`() =
        FxTestSupport.run {
            val panel = panel()
            val drawingView = panel.field<SimpleDrawingView>("drawingView")
            val viewport = panel.field<StackPane>("editorViewport")
            panel.drawRect(20.0, 20.0, 40.0, 30.0, "#ff0000")
            drawingView.selectedFigures.add(panel.figures().single())

            viewport.fireEvent(keyPressed(KeyCode.BACK_SPACE))

            assertTrue(panel.figures().isEmpty())
        }

    @Test
    fun `context menu delete action removes selected canvas figure`() =
        FxTestSupport.run {
            val panel = panel()
            val drawingView = panel.field<SimpleDrawingView>("drawingView")
            panel.drawCircle(40.0, 40.0, 10.0, "#ff0000")
            drawingView.selectedFigures.add(panel.figures().single())

            panel.deleteSelectedFigures()

            assertTrue(panel.figures().isEmpty())
        }

    @Test
    fun `toolbar delete button is enabled only for deletable selection`() =
        FxTestSupport.run {
            val panel = panel()
            val drawingView = panel.field<SimpleDrawingView>("drawingView")
            val toolbarButtons = descendants(panel.field<CanvasToolbar>("toolbar")).filterIsInstance<Button>()
            val deleteButton = toolbarButtons.first { it.text == "Delete" }
            panel.drawRect(20.0, 20.0, 40.0, 30.0, "#ff0000")

            assertTrue(deleteButton.isDisable)
            drawingView.selectedFigures.add(panel.figures().single())
            panel.field<CanvasSelectionDeletionController>("selectionDeletionController").refreshSelectionActions()
            assertEquals(false, deleteButton.isDisable)

            deleteButton.fire()

            assertTrue(panel.figures().isEmpty())
            assertTrue(deleteButton.isDisable)
        }

    private fun panel() = CanvasPanel(preferenceStore)

    private fun keyPressed(code: KeyCode): KeyEvent =
        KeyEvent(
            KeyEvent.KEY_PRESSED,
            "",
            "",
            code,
            false,
            false,
            false,
            false,
        )

    companion object {
        @JvmStatic
        @BeforeAll
        fun startToolkit() = FxTestSupport.startToolkit()
    }
}
