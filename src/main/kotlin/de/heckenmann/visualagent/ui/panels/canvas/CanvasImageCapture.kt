package de.heckenmann.visualagent.ui.panels.canvas

import de.heckenmann.visualagent.image.PngEncoder
import javafx.scene.Node
import javafx.scene.SnapshotParameters

/**
 * Encodes JavaFX canvas nodes into immutable image snapshots.
 */
internal object CanvasImageCapture {
    /**
     * Renders a node to PNG or JPG bytes.
     *
     * @param node JavaFX node to snapshot
     * @param requestedFormat Requested output format, `png`, `jpg`, or `jpeg`
     * @return Encoded immutable image snapshot
     */
    fun capture(
        node: Node,
        requestedFormat: String,
    ): CanvasImageSnapshot {
        val format = normalizeImageFormat(requestedFormat)
        val snapshot = node.snapshot(SnapshotParameters(), null)
        val bytes = PngEncoder.encode(snapshot)
        return CanvasImageSnapshot(
            format = format,
            mimeType = "image/png",
            bytes = bytes,
            width = snapshot.width.toInt(),
            height = snapshot.height.toInt(),
        )
    }

    private fun normalizeImageFormat(format: String): String =
        when (format.lowercase()) {
            "png", "" -> PNG_FORMAT
            else -> throw IllegalArgumentException("Unsupported canvas image format: $format")
        }

    private const val PNG_FORMAT = "png"
}
