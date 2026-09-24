package de.heckenmann.visualagent.agent.codex

import org.junit.jupiter.api.Test
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.prompt.Prompt
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals

/** Verifies section boundaries in native Codex assistant item streams. */
class CodexAppServerStreamingBoundaryTest {
    private val fixture = CodexAppServerChatModelTest()

    @Test
    fun `separates distinct assistant items in streaming and complete responses`() =
        run {
            val directory = createTempDirectory("codex-app-server-item-boundary-test-")
            val executable = fixture.fakeServer(directory, secondItem = true)
            try {
                val model = CodexAppServerChatModel(executable, "gpt-test", emptyList(), directory)
                val responses =
                    model
                        .streamReactive(Prompt("hello"))
                        .collectList()
                        .block()
                        .orEmpty()

                assertEquals(
                    listOf("item-1", "item-2"),
                    responses.dropLast(1).map { it.metadata.get<String>("codexItemId") ?: error("item ID missing") },
                )
                assertEquals(listOf("hel", "\n\nlo"), responses.dropLast(1).map(::responseText))
                val completed = requireNotNull(model.completeReactive(Prompt("hello")).block())
                assertEquals("hel\n\nlo", responseText(completed))
            } finally {
                fixture.deleteRecursively(directory)
            }
        }

    @Test
    fun `does not duplicate an existing Markdown boundary between items`() =
        run {
            val directory = createTempDirectory("codex-app-server-existing-boundary-test-")
            val executable = fixture.fakeServer(directory, secondItem = true, firstDelta = "First.\n\n")
            try {
                val responses =
                    CodexAppServerChatModel(executable, "gpt-test", emptyList(), directory)
                        .streamReactive(Prompt("hello"))
                        .collectList()
                        .block()
                        .orEmpty()

                assertEquals(listOf("First.\n\n", "lo"), responses.dropLast(1).map(::responseText))
            } finally {
                fixture.deleteRecursively(directory)
            }
        }

    @Test
    fun `preserves leading whitespace at a Codex item boundary`() =
        run {
            val directory = createTempDirectory("codex-app-server-leading-whitespace-test-")
            val executable = fixture.fakeServer(directory, secondItem = true, firstDelta = "First.", secondDelta = " next")
            try {
                val responses =
                    CodexAppServerChatModel(executable, "gpt-test", emptyList(), directory)
                        .streamReactive(Prompt("hello"))
                        .collectList()
                        .block()
                        .orEmpty()

                assertEquals(listOf("First.", " next"), responses.dropLast(1).map(::responseText))
            } finally {
                fixture.deleteRecursively(directory)
            }
        }

    private fun responseText(response: ChatResponse): String =
        response.result
            ?.output
            ?.text
            .orEmpty()
}
