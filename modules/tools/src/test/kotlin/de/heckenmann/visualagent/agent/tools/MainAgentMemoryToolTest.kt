package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.MainAgentMemoryEditResult
import de.heckenmann.visualagent.agent.tools.api.MainAgentMemoryPort
import de.heckenmann.visualagent.agent.tools.api.MainAgentMemorySnapshot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Verifies the model-facing durable main-agent memory contract. */
class MainAgentMemoryToolTest {
    @Test
    fun `show returns the current revisioned document`() {
        val tool = MainAgentMemoryTool(RecordingMemoryPort())

        val result = tool.execute("{\"action\":\"show\"}")

        assertTrue(result.success)
        assertTrue(result.content.contains("durable note"))
        val revision =
            result.data!!
                .jsonObject["revision"]!!
                .jsonPrimitive
                .content
                .toInt()
        assertEquals(4, revision)
    }

    @Test
    fun `edit accepts a numeric revision and returns a conflict without overwriting`() {
        val port = RecordingMemoryPort(conflict = true)
        val tool = MainAgentMemoryTool(port)

        val result = tool.execute("{\"action\":\"edit\",\"content\":\"replacement\",\"expectedRevision\":4}")

        assertFalse(result.success)
        assertEquals("replacement", port.lastContent)
        assertEquals(
            5,
            result.data!!
                .jsonObject["revision"]!!
                .jsonPrimitive.content
                .toInt(),
        )
    }

    @Test
    fun `registry exposes the snapshot as structured envelope data`() {
        val tool = MainAgentMemoryTool(RecordingMemoryPort())
        val registry = ToolRegistry(listOf(tool), ToolEventBus()) { 120 }

        val response = registry.execute(registry.resolve(setOf(tool.definition.id)).single(), "{\"action\":\"show\"}", emptyMap())
        val envelope = Json.parseToJsonElement(response).jsonObject

        assertEquals("durable note", envelope["data"]!!.jsonObject["content"]!!.jsonPrimitive.content)
        assertEquals("4", envelope["data"]!!.jsonObject["revision"]!!.jsonPrimitive.content)
    }

    private class RecordingMemoryPort(
        private val conflict: Boolean = false,
    ) : MainAgentMemoryPort {
        var lastContent = ""
        private val current = MainAgentMemorySnapshot("durable note", 4, 12, 12_000)

        override fun show(): MainAgentMemorySnapshot = current

        override fun edit(
            content: String,
            expectedRevision: Long,
        ): MainAgentMemoryEditResult {
            lastContent = content
            return if (conflict) {
                MainAgentMemoryEditResult.Conflict(current.copy(revision = 5))
            } else {
                MainAgentMemoryEditResult.Saved(current.copy(content = content, revision = 5))
            }
        }
    }
}
