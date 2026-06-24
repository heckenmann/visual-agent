package de.heckenmann.visualagent.ui.panels.canvas

import de.heckenmann.visualagent.ui.panels.FxTestSupport
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.BeansException
import org.springframework.beans.factory.ObjectProvider
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CanvasServiceTest {
    @Test
    fun `service delegates drawing and clearing to live canvas panel`() =
        FxTestSupport.run {
            val panel = CanvasPanel(InMemoryPreferenceStore())
            val service = CanvasService(SingleObjectProvider(panel))

            val drawn = service.drawText("Hello", 12.0, 18.0, "#24292f")
            val cleared = service.clear()

            assertEquals(1, drawn.figureCount)
            assertEquals("text", drawn.figures.single().type)
            assertEquals(0, cleared.figureCount)
        }

    @Test
    fun `service reports live canvas snapshots`() =
        FxTestSupport.run {
            val panel = CanvasPanel(InMemoryPreferenceStore())
            panel.resize(320.0, 240.0)
            panel.layout()
            val service = CanvasService(SingleObjectProvider(panel))

            service.drawRect(10.0, 20.0, 30.0, 40.0, "#ffffff", "#000000")
            val snapshot = service.snapshot()

            assertEquals(1, snapshot.figureCount)
            assertEquals("rectangle", snapshot.figures.single().type)
        }

    @Test
    fun `service captures live canvas as image bytes`() =
        FxTestSupport.run {
            val panel = CanvasPanel(InMemoryPreferenceStore())
            panel.resize(320.0, 240.0)
            panel.layout()
            val service = CanvasService(SingleObjectProvider(panel))

            service.drawRect(10.0, 20.0, 30.0, 40.0, "#ffffff", "#000000")
            val snapshot = service.captureImage("png")

            assertEquals("png", snapshot.format)
            assertEquals("image/png", snapshot.mimeType)
            assertTrue(snapshot.bytes.isNotEmpty())
            assertTrue(snapshot.width > 0)
            assertTrue(snapshot.height > 0)
        }

    @Test
    fun `service rejects unsupported canvas image formats`() =
        FxTestSupport.run {
            val panel = CanvasPanel(InMemoryPreferenceStore())
            panel.resize(320.0, 240.0)
            panel.layout()
            val service = CanvasService(SingleObjectProvider(panel))

            val error =
                assertFailsWith<IllegalArgumentException> {
                    service.captureImage("jpg")
                }

            assertEquals("Unsupported canvas image format: jpg", error.message)
        }

    @Test
    fun `canvas panel stores configured surface size`() =
        FxTestSupport.run {
            val preferences = InMemoryPreferenceStore()
            val panel = CanvasPanel(preferences)

            panel.setCanvasSize(1600.0, 900.0)
            val recreated = CanvasPanel(preferences)

            assertEquals(1600.0, panel.canvasSize().first)
            assertEquals(900.0, panel.canvasSize().second)
            assertEquals(1600.0, recreated.canvasSize().first)
            assertEquals(900.0, recreated.canvasSize().second)
        }

    companion object {
        @JvmStatic
        @BeforeAll
        fun startToolkit() = FxTestSupport.startToolkit()
    }

    private class SingleObjectProvider<T : Any>(
        private val value: T,
    ) : ObjectProvider<T> {
        override fun getObject(): T = value

        override fun getObject(vararg args: Any?): T = value

        override fun getIfAvailable(): T = value

        override fun getIfUnique(): T = value

        override fun iterator(): MutableIterator<T> = mutableListOf(value).iterator()

        @Throws(BeansException::class)
        override fun stream(): Stream<T> = Stream.of(value)

        @Throws(BeansException::class)
        override fun orderedStream(): Stream<T> = stream()
    }
}
