package de.heckenmann.visualagent.agent.javascript

import de.heckenmann.visualagent.agent.tools.TaskToolAccess
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.agent.tools.ToolRegistry
import de.heckenmann.visualagent.task.TaskPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JavaScriptTaskScopeTest {
    @Test
    fun `workspace helpers cannot bypass read only task authority`() {
        var writes = 0
        var deletes = 0
        val writer =
            object : JavaScriptWorkspaceWriter {
                override fun write(
                    relativePath: String,
                    content: String,
                ): JavaScriptWorkspaceWriteResult {
                    writes++
                    return JavaScriptWorkspaceWriteResult(relativePath, content.length.toLong(), "text/plain")
                }

                override fun read(
                    relativePath: String,
                    maxBytes: Long,
                ) = JavaScriptWorkspaceReadResult(relativePath, "Evidence", 8)

                override fun delete(relativePath: String): JavaScriptWorkspaceDeleteResult {
                    deletes++
                    return JavaScriptWorkspaceDeleteResult(relativePath, true)
                }
            }
        val access =
            TaskToolAccess(TaskPolicy(actions = mapOf("workspace:file" to listOf("readText")), resources = listOf("input")), {
                setOf("workspace:file", "javascript:execute")
            }, { true })
        GraalJavaScriptExecutionService({ ToolRegistry(emptyList(), ToolEventBus()) }, writer, preferIsolate = false).use { service ->
            fun execute(source: String) =
                service.execute(
                    JavaScriptExecutionRequest(
                        source,
                        enabledTools = emptySet(),
                        requestContext =
                            mapOf(
                                "taskToolAccess" to access,
                            ),
                    ),
                )
            assertEquals("Evidence", execute("return workspace.read({path:'input/source.txt'});").value)
            listOf(
                "return workspace.write({path:'input/output.txt',content:'escape'});",
                "return workspace.delete({path:'input/source.txt'});",
                "return workspace.read({path:'other/source.txt'});",
            ).forEach { source ->
                assertFailsWith<JavaScriptExecutionException> { execute(source) }
            }
            assertEquals(0, writes)
            assertEquals(0, deletes)
        }
    }
}
