package de.heckenmann.visualagent.agent.javascript

import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.agent.tools.ToolRegistry
import org.junit.jupiter.api.AfterEach
import java.nio.file.NoSuchFileException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JavaScriptWorkspaceBridgeTest {
    private val events = ToolEventBus()
    private val registry = ToolRegistry(emptyList(), events)
    private val workspaceFiles = mutableMapOf<String, String>()
    private val workspaceWriter =
        object : JavaScriptWorkspaceWriter {
            override fun write(
                relativePath: String,
                content: String,
            ): JavaScriptWorkspaceWriteResult {
                validate(relativePath)
                workspaceFiles[relativePath] = content
                return JavaScriptWorkspaceWriteResult(relativePath, content.length.toLong(), "text/plain")
            }

            override fun read(relativePath: String): String {
                validate(relativePath)
                return workspaceFiles[relativePath] ?: error("Workspace file does not exist")
            }

            override fun delete(relativePath: String): JavaScriptWorkspaceDeleteResult {
                validate(relativePath)
                return JavaScriptWorkspaceDeleteResult(relativePath, workspaceFiles.remove(relativePath) != null)
            }

            private fun validate(relativePath: String) {
                require(relativePath.split('/').none { it == "." || it == ".." }) { "Workspace path traversal is not allowed" }
            }
        }
    private val service = GraalJavaScriptExecutionService({ registry }, workspaceWriter)

    @AfterEach
    fun close() {
        service.close()
        registry.close()
    }

    @Test
    fun `writes reads and deletes through the hardened workspace helper`() {
        val result =
            execute(
                "const saved = workspace.write({path: 'reports/result.md', content: '# Result'}); " +
                    "const loaded = workspace.read({path: 'reports/result.md'}); " +
                    "const removed = workspace.delete({path: 'reports/result.md'}); " +
                    "return {saved, loaded, removed};",
            )

        assertEquals(
            mapOf(
                "saved" to mapOf("path" to "reports/result.md", "sizeBytes" to 8.0, "mimeType" to "text/plain"),
                "loaded" to "# Result",
                "removed" to mapOf("path" to "reports/result.md", "deleted" to true),
            ),
            result.value,
        )
        assertTrue(workspaceFiles.isEmpty())
    }

    @Test
    fun `workspace helper rejects traversal and exposes the error to the script`() {
        val result =
            execute(
                "try { workspace.write({path: '../escape.txt', content: 'x'}); return 'unexpected'; } catch (error) { return error.message; }",
            )

        assertTrue(result.value.toString().contains("traversal"))
        assertTrue(workspaceFiles.isEmpty())
    }

    @Test
    fun `workspace failures do not expose server paths`() {
        val failingWriter =
            object : JavaScriptWorkspaceWriter {
                override fun write(
                    relativePath: String,
                    content: String,
                ) = JavaScriptWorkspaceWriteResult(
                    relativePath,
                    content.length.toLong(),
                    "text/plain",
                )

                override fun read(relativePath: String): String =
                    throw NoSuchFileException("/srv/visual-agent/data/workspace/$relativePath")
            }
        val failingService = GraalJavaScriptExecutionService({ registry }, failingWriter)
        try {
            val error =
                assertFailsWith<JavaScriptExecutionException> {
                    failingService.execute(JavaScriptExecutionRequest("return workspace.read({path: 'missing.txt'});", emptySet()))
                }

            assertTrue(error.message.contains("Workspace read failed"))
            assertTrue("/srv/visual-agent" !in error.message)
        } finally {
            failingService.close()
        }
    }

    @Test
    fun `model facing tool executes a workspace JavaScript file`() {
        workspaceFiles["scripts/value.js"] = "return {answer: 42};"
        val tool = JavaScriptExecuteTool(service)
        val result = tool.execute("""{"path":"scripts/value.js"}""", emptyMap())

        assertTrue(result.success)
        assertEquals("{\"answer\":42.0}", result.content)
    }

    @Test
    fun `sandbox does not expose host filesystem environment or classes`() {
        listOf(
            "Java.type('java.lang.System')",
            "Polyglot.import('secret')",
            "(new Function('return process'))()",
        ).forEach { expression ->
            assertFailsWith<JavaScriptExecutionException> { execute("return $expression;") }
        }
    }

    private fun execute(source: String): JavaScriptExecutionResult =
        service.execute(JavaScriptExecutionRequest(source = source, enabledTools = emptySet()))
}
