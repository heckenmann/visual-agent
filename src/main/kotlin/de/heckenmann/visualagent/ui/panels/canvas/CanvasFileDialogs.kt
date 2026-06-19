package de.heckenmann.visualagent.ui.panels.canvas

import javafx.stage.FileChooser
import javafx.stage.Window
import java.io.File

/**
 * Creates canvas import/export file dialogs with consistent filters.
 */
internal object CanvasFileDialogs {
    fun showImageOpenDialog(owner: Window?): File? =
        FileChooser()
            .apply {
                title = "Insert Image"
                extensionFilters.add(FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"))
            }.showOpenDialog(owner)

    fun showPngSaveDialog(owner: Window?): File? =
        FileChooser()
            .apply {
                title = "Export Canvas"
                initialFileName = "visual-agent-canvas.png"
                extensionFilters.add(FileChooser.ExtensionFilter("PNG image", "*.png"))
            }.showSaveDialog(owner)
}
