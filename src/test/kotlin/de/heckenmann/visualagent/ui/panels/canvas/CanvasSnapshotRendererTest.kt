package de.heckenmann.visualagent.ui.panels.canvas

import de.heckenmann.visualagent.ui.panels.FxTestSupport
import javafx.scene.Scene
import javafx.scene.layout.Region
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CanvasSnapshotRendererTest {
    @Test
    fun `renderer falls back to logical scale when node is not attached to a window`() =
        FxTestSupport.run {
            val node =
                Region().apply {
                    resize(80.0, 40.0)
                }

            assertEquals(1.0, CanvasSnapshotRenderer.renderScale(node))
            val snapshot = CanvasSnapshotRenderer.snapshot(node)
            assertEquals(80.0, snapshot.width, 0.001)
            assertEquals(40.0, snapshot.height, 0.001)
        }

    @Test
    fun `service capture uses rendered node dimensions`() =
        FxTestSupport.run {
            val panel = CanvasPanel(InMemoryPreferenceStore())
            Scene(panel, 640.0, 480.0)
            panel.resize(640.0, 480.0)
            panel.layout()

            val snapshot = panel.captureImage("png")

            assertTrue(snapshot.width >= 1)
            assertTrue(snapshot.height >= 1)
            assertTrue(snapshot.bytes.isNotEmpty())
        }

    companion object {
        @JvmStatic
        @BeforeAll
        fun startToolkit() = FxTestSupport.startToolkit()
    }
}
