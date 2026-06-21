package de.heckenmann.visualagent.ui.panels.canvas

import de.heckenmann.visualagent.image.PngEncoder
import javafx.scene.SnapshotParameters
import org.jhotdraw8.draw.SimpleDrawingView
import java.nio.file.Files

/**
 * Exports the current editable canvas view to a PNG file.
 *
 * Use cases: UC-0000028, UC-0000066.
 */
internal object CanvasPngExporter {
    /** Clears selection handles and writes a PNG snapshot to a user-selected file. */
    fun export(drawingView: SimpleDrawingView) {
        val file = CanvasFileDialogs.showPngSaveDialog(drawingView.node.scene?.window) ?: return
        drawingView.clearSelection()
        val snapshot = drawingView.node.snapshot(SnapshotParameters(), null)
        Files.write(file.toPath(), PngEncoder.encode(snapshot))
    }
}
