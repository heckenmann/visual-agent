package de.heckenmann.visualagent.ui.panels.canvas

import de.heckenmann.visualagent.image.PngEncoder
import javafx.scene.Node

/**
 * Encodes JavaFX canvas nodes into immutable image snapshots.
 */
internal object CanvasImageCapture {
    /**
     * Renders a node to PNG bytes.
     *
     * @param node JavaFX node to snapshot
     * @param requestedFormat Requested output format, currently `png`
     * @return Encoded immutable image snapshot
     */
    fun capture(
        node: Node,
        requestedFormat: String,
    ): CanvasImageSnapshot {
        val format = normalizeImageFormat(requestedFormat)
        val snapshot = CanvasSnapshotRenderer.snapshot(node)
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
