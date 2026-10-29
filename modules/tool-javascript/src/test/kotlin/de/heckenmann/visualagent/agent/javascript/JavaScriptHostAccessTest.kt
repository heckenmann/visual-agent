package de.heckenmann.visualagent.agent.javascript

import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.agent.tools.ToolRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Keeps Java host access disabled regardless of native isolate availability. */
class JavaScriptHostAccessTest {
    @Test
    fun `host builtins and class lookup remain unavailable in both sandbox paths`() {
        listOf(false, true).forEach { preferIsolate ->
            GraalJavaScriptExecutionService(
                { ToolRegistry(emptyList(), ToolEventBus()) },
                JavaScriptWorkspaceWriter { _, _ -> error("Unused") },
                preferIsolate = preferIsolate,
            ).use { service ->
                fun execute(source: String) = service.execute(JavaScriptExecutionRequest(source, enabledTools = emptySet()))

                assertEquals("undefined", execute("return typeof Java;").value)
                listOf(
                    "return Java.type('java.lang.System').getProperties();",
                    "return Java.type('java.lang.Thread').currentThread();",
                    "return Java.type('java.lang.Runtime').getRuntime();",
                ).forEach { source ->
                    assertFailsWith<JavaScriptExecutionException> { execute(source) }
                }
            }
        }
    }
}
