package de.heckenmann.visualagent.agent

import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.ToolResponseMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.model.tool.DefaultToolCallingManager
import org.springframework.ai.model.tool.ToolCallLimitExceededException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Covers current-turn limit preservation across native rounds and user-turn reset. */
class NativeToolCallLimitsTest {
    @Test
    fun `duplicate native calls cannot cross the per-tool allowance`() {
        val prior = responses(List(DefaultToolCallingManager.DEFAULT_MAX_CALLS_PER_TOOL - 1) { "read" })
        val error =
            assertFailsWith<ToolCallLimitExceededException> {
                validateNativeToolCallLimits(Prompt(listOf(UserMessage("Inspect"), prior)), calls(listOf("read", "read")))
            }
        assertEquals("read", error.toolName)
        assertEquals(DefaultToolCallingManager.DEFAULT_MAX_CALLS_PER_TOOL, error.limit)
    }

    @Test
    fun `different native tools still share the total turn allowance`() {
        val count = DefaultToolCallingManager.DEFAULT_MAX_TOTAL_TOOL_CALLS
        val prior = responses(List(count) { "read$it" })
        val error =
            assertFailsWith<ToolCallLimitExceededException> {
                validateNativeToolCallLimits(Prompt(listOf(UserMessage("Inspect"), prior)), calls(listOf("other")))
            }
        assertEquals(null, error.toolName)
        assertEquals(count, error.limit)
    }

    @Test
    fun `new user turn resets previous allowances`() {
        val prior = responses(List(DefaultToolCallingManager.DEFAULT_MAX_CALLS_PER_TOOL) { "read" })
        validateNativeToolCallLimits(Prompt(listOf(UserMessage("Old"), prior, UserMessage("New"))), calls(listOf("read")))
    }

    private fun calls(names: List<String>) = names.mapIndexed { index, name -> ProviderToolCall("$index", "function", name, "{}") }

    private fun responses(names: List<String>) =
        ToolResponseMessage
            .builder()
            .responses(
                names.mapIndexed { index, name -> ToolResponseMessage.ToolResponse("$index", name, "done") },
            ).build()
}
