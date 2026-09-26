package de.heckenmann.visualagent.ui.conversation

import androidx.compose.foundation.v2.ScrollbarAdapter

/** Maps scrollbar positions to a reverse-layout conversation list. */
internal class ConversationReversedScrollbarAdapter(
    private val delegate: ScrollbarAdapter,
) : ScrollbarAdapter {
    override val scrollOffset: Double
        get() = maxScrollOffset - delegate.scrollOffset

    override val contentSize: Double
        get() = delegate.contentSize

    override val viewportSize: Double
        get() = delegate.viewportSize

    override suspend fun scrollTo(scrollOffset: Double) {
        delegate.scrollTo((maxScrollOffset - scrollOffset).coerceIn(0.0, maxScrollOffset))
    }

    private val maxScrollOffset: Double
        get() = (contentSize - viewportSize).coerceAtLeast(0.0)
}
