package de.heckenmann.visualagent.ui.panels.canvas

import javafx.application.Platform
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * JavaFX-thread-safe bridge between model tools and the live canvas panel.
 */
@Service
class CanvasService(
    private val canvasPanelProvider: ObjectProvider<CanvasPanel>,
) : CanvasOperations {
    override fun snapshot(): CanvasSnapshot = onFxThread { canvasPanel.snapshot() }

    override fun clear(): CanvasSnapshot = onFxThread { canvasPanel.clearCanvasAndSnapshot() }

    override fun drawText(
        text: String,
        x: Double,
        y: Double,
        color: String,
    ): CanvasSnapshot = onFxThread { canvasPanel.drawText(text, x, y, color).let { canvasPanel.snapshot() } }

    override fun drawRect(
        x: Double,
        y: Double,
        width: Double,
        height: Double,
        fillColor: String,
        strokeColor: String?,
    ): CanvasSnapshot = onFxThread { canvasPanel.drawRect(x, y, width, height, fillColor, strokeColor).let { canvasPanel.snapshot() } }

    override fun drawLine(
        x1: Double,
        y1: Double,
        x2: Double,
        y2: Double,
        color: String,
        width: Double,
    ): CanvasSnapshot = onFxThread { canvasPanel.drawLine(x1, y1, x2, y2, color, width).let { canvasPanel.snapshot() } }

    override fun drawCircle(
        centerX: Double,
        centerY: Double,
        radius: Double,
        fillColor: String,
    ): CanvasSnapshot = onFxThread { canvasPanel.drawCircle(centerX, centerY, radius, fillColor).let { canvasPanel.snapshot() } }

    override fun insertImage(path: String): CanvasSnapshot =
        onFxThread {
            canvasPanel.addImage(File(path))
            canvasPanel.snapshot()
        }

    private fun <T> onFxThread(action: () -> T): T {
        if (Platform.isFxApplicationThread()) return action()
        val completed = CountDownLatch(1)
        val result = AtomicReference<Result<T>>()
        Platform.runLater {
            result.set(runCatching(action))
            completed.countDown()
        }
        check(completed.await(FX_OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "Canvas operation timed out" }
        return result.get().getOrThrow()
    }

    private val canvasPanel: CanvasPanel
        get() = canvasPanelProvider.getObject()

    private companion object {
        const val FX_OPERATION_TIMEOUT_SECONDS = 10L
    }
}
