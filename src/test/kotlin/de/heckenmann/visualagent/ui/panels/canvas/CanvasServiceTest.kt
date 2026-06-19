package de.heckenmann.visualagent.ui.panels.canvas

import de.heckenmann.visualagent.ui.panels.FxTestSupport
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.BeansException
import org.springframework.beans.factory.ObjectProvider
import java.util.stream.Stream
import kotlin.test.assertEquals

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
            val service = CanvasService(SingleObjectProvider(panel))

            service.drawRect(10.0, 20.0, 30.0, 40.0, "#ffffff", "#000000")
            val snapshot = service.snapshot()

            assertEquals(1, snapshot.figureCount)
            assertEquals("rectangle", snapshot.figures.single().type)
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
