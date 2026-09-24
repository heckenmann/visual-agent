package de.heckenmann.visualagent.agent

import kotlin.test.Test
import kotlin.test.assertEquals

/** Tests Markdown boundaries inserted between logical streamed assistant sections. */
class StreamSectionBoundaryTest {
    @Test
    fun `adds a blank line between visible sections`() {
        assertEquals("\n\n", StreamSectionBoundary.prefix("Before.", "After."))
    }

    @Test
    fun `does not add a prefix when either section is not visible`() {
        assertEquals("", StreamSectionBoundary.prefix("", "After."))
        assertEquals("", StreamSectionBoundary.prefix("Before.", " \n"))
        assertEquals("", StreamSectionBoundary.prefix("Before.", "After.", hasPreviousVisibleText = false))
    }

    @Test
    fun `preserves provider whitespace and existing Markdown boundaries`() {
        assertEquals("", StreamSectionBoundary.prefix("Before.", " After."))
        assertEquals("", StreamSectionBoundary.prefix("Before.\n\n", "After."))
        assertEquals("", StreamSectionBoundary.prefix("Before.\r\n\r\n", "After."))
        assertEquals("\n", StreamSectionBoundary.prefix("Before.\n", "After."))
    }

    @Test
    fun `separates the message and provider turn consistently`() {
        val response =
            ChatResponse(
                model = "test-model",
                message = Message("assistant", "Final answer."),
                done = true,
                providerTurn = ProviderTurnResponse(model = "test-model", content = "Final answer."),
            )

        val separated = StreamSectionBoundary.separateResponse("Initial update.", response)

        assertEquals("\n\nFinal answer.", separated.message.content)
        assertEquals("\n\nFinal answer.", separated.providerTurn?.content)
    }

    @Test
    fun `leaves empty and already separated final responses unchanged`() {
        val empty = ChatResponse("model", Message("assistant", ""), done = true)
        val separated = ChatResponse("model", Message("assistant", "\n\nFinal"), done = true)

        assertEquals(empty, StreamSectionBoundary.separateResponse("Initial", empty))
        assertEquals(separated, StreamSectionBoundary.separateResponse("Initial", separated))
    }
}
