package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import de.heckenmann.visualagent.task.TaskPolicy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TaskToolAccessTest {
    @Test
    fun `scope narrows agent authority and explicit empty tools deny all`() {
        val denied = TaskToolAccess(TaskPolicy(tools = emptyList()), { setOf("workspace:file") }, { true })
        assertFailsWith<IllegalArgumentException> {
            denied.requireAllowed("workspace:file", input("""{"action":"readText","path":"work/a.txt"}"""))
        }
        val revoked = TaskToolAccess(TaskPolicy(), { emptySet() }, { true })
        assertFailsWith<IllegalArgumentException> { revoked.requireAllowed("workspace:file", input("{}")) }
    }

    @Test
    fun `resources reject traversal sibling files opaque ids and external roots`() {
        val access = TaskToolAccess(TaskPolicy(resources = listOf("work")), { setOf("workspace:file") }, { true })
        access.requireAllowed("workspace:file", input("""{"action":"readText","path":"work/a.txt"}"""))
        listOf(
            """{"path":"work-other/a.txt"}""",
            """{"path":"work/../other.txt"}""",
            """{"id":"hidden"}""",
            """{"rootId":"grant-1","path":"work/a.txt"}""",
            """{"sourceRootId":"workspace","sourcePath":"work/a.txt","targetRootId":"workspace","targetPath":"elsewhere/a.txt"}""",
        ).forEach { json -> assertFailsWith<IllegalArgumentException> { access.requireAllowed("workspace:file", input(json)) } }
    }

    @Test
    fun `registry denies before side effects and records terminal evidence including revoked retries`() {
        var current = true
        var calls = 0
        val tool =
            object : VisualAgentTool {
                override val definition = ToolDefinition(ToolId("workspace:file"), "workspace_file", "Test", "{}")

                override fun execute(
                    inputJson: String,
                    context: Map<String, Any>,
                ): ToolResult {
                    calls++
                    return success("workspace:file", "written")
                }
            }
        val registry = ToolRegistry(listOf(tool), ToolEventBus())
        val scope = ToolExecutionScope()
        val context =
            mapOf(
                "toolExecutionScope" to scope,
                "taskToolAccess" to
                    TaskToolAccess(
                        TaskPolicy(actions = mapOf("workspace:file" to listOf("readText")), resources = listOf("work")),
                        { setOf("workspace:file") },
                        { current },
                    ),
            )
        assertFalse(registry.executeBlockingResult(tool, """{"action":"writeText","path":"work/a.txt"}""", context).success)
        assertEquals(0, calls)
        assertTrue(registry.executeBlockingResult(tool, """{"action":"readText","path":"work/a.txt"}""", context).success)
        current = false
        assertFalse(registry.executeBlockingResult(tool, """{"action":"readText","path":"work/a.txt"}""", context).success)
        assertEquals(1, calls)
        assertEquals(listOf(false, true, false), scope.observations().map { it.success })
    }

    private fun input(text: String) = Json.parseToJsonElement(text).jsonObject

    @Test
    fun `directory scope checks the derived destination instead of unused path arguments`() {
        val access = TaskToolAccess(TaskPolicy(resources = listOf("work")), { setOf("workspace:file") }, { true })
        assertFailsWith<IllegalArgumentException> {
            access.requireAllowed("workspace:file", input("""{"action":"createDirectory","path":"work","name":"outside"}"""))
        }
        access.requireAllowed("workspace:file", input("""{"action":"createDirectory","name":"work"}"""))
        access.requireAllowed("workspace:file", input("""{"action":"createDirectory","parentDirectory":"work","name":"child"}"""))
        assertFailsWith<IllegalArgumentException> {
            access.requireAllowed("workspace:file", input("""{"action":"createDirectory","parentDirectory":"work","name":"../escape"}"""))
        }
    }
}
