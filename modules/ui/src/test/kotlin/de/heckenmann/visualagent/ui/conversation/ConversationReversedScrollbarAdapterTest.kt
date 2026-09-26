package de.heckenmann.visualagent.ui.conversation

import androidx.compose.foundation.v2.ScrollbarAdapter
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

/** Verifies that scrollbar positions map in the same direction as a reverse-layout list. */
class ConversationReversedScrollbarAdapterTest {
    @Test
    fun `reverses offset while preserving content dimensions`() {
        val delegate = TestScrollbarAdapter(scrollOffset = 12.0, contentSize = 100.0, viewportSize = 20.0)
        val adapter = ConversationReversedScrollbarAdapter(delegate)

        assertEquals(68.0, adapter.scrollOffset)
        assertEquals(100.0, adapter.contentSize)
        assertEquals(20.0, adapter.viewportSize)
    }

    @Test
    fun `maps scrollbar drag back to reverse list coordinates`() =
        runTest {
            val delegate = TestScrollbarAdapter(scrollOffset = 12.0, contentSize = 100.0, viewportSize = 20.0)
            val adapter = ConversationReversedScrollbarAdapter(delegate)

            adapter.scrollTo(25.0)

            assertEquals(55.0, delegate.requestedOffset)
        }

    @Test
    fun `clamps dragged offset to available scroll range`() =
        runTest {
            val delegate = TestScrollbarAdapter(scrollOffset = 0.0, contentSize = 100.0, viewportSize = 20.0)
            val adapter = ConversationReversedScrollbarAdapter(delegate)

            adapter.scrollTo(200.0)

            assertEquals(0.0, delegate.requestedOffset)
        }

    private class TestScrollbarAdapter(
        override val scrollOffset: Double,
        override val contentSize: Double,
        override val viewportSize: Double,
    ) : ScrollbarAdapter {
        var requestedOffset: Double? = null
            private set

        override suspend fun scrollTo(scrollOffset: Double) {
            requestedOffset = scrollOffset
        }
    }
}
