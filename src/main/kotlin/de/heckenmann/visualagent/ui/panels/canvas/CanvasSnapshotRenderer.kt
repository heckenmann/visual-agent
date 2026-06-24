package de.heckenmann.visualagent.ui.panels.canvas

import javafx.scene.Node
import javafx.scene.SnapshotParameters
import javafx.scene.image.WritableImage
import javafx.scene.transform.Transform
import kotlin.math.ceil

/**
 * Creates JavaFX snapshots that preserve detail on high-DPI displays.
 */
internal object CanvasSnapshotRenderer {
    /**
     * Snapshots the node at the JavaFX render scale reported by the current window.
     *
     * @param node Node to snapshot
     * @return Snapshot image using at least logical 1x scale
     */
    fun snapshot(node: Node): WritableImage {
        val scale = renderScale(node)
        val width = ceil(node.boundsInLocal.width * scale).toInt().coerceAtLeast(1)
        val height = ceil(node.boundsInLocal.height * scale).toInt().coerceAtLeast(1)
        val parameters =
            SnapshotParameters().apply {
                transform = Transform.scale(scale, scale)
            }
        return node.snapshot(parameters, WritableImage(width, height))
    }

    /**
     * Reads the maximum render scale for the node window.
     *
     * @param node Node whose scene/window supplies the render scale
     * @return Positive finite scale, or 1.0 when no window scale is available
     */
    fun renderScale(node: Node): Double {
        val window = node.scene?.window ?: return DEFAULT_SCALE
        val outputScaleX = window.outputScaleX.takeIf(Double::isFinite)?.takeIf { it > 0.0 } ?: DEFAULT_SCALE
        val outputScaleY = window.outputScaleY.takeIf(Double::isFinite)?.takeIf { it > 0.0 } ?: DEFAULT_SCALE
        return maxOf(DEFAULT_SCALE, outputScaleX, outputScaleY)
    }

    private const val DEFAULT_SCALE = 1.0
}
