package de.heckenmann.visualagent.agent.tools

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileToolsTest {
    @Test
    fun `file tools support write read edit list glob and grep`() {
        val directory = testDirectory()
        val relativeFile = "$directory/notes.txt"

        assertTrue(FileWriteTool().execute("""{"path":"$relativeFile","content":"Alpha\nBeta"}""").success)
        assertTrue(FileReadTool().execute("""{"path":"$relativeFile"}""").content.contains("Alpha"))
        assertTrue(FileEditTool().execute("""{"path":"$relativeFile","oldText":"Beta","newText":"Gamma"}""").success)
        assertTrue(FileListTool().execute("""{"path":"$directory"}""").content.contains("notes.txt"))
        assertTrue(FileGlobTool().execute("""{"pattern":"$directory/*.txt"}""").content.contains(relativeFile))

        val grep = FileGrepTool().execute("""{"path":"$directory","query":"gamma"}""")
        assertTrue(grep.success)
        assertTrue(grep.content.contains("notes.txt:2: Gamma"))
    }

    @Test
    fun `file tools reject invalid paths and missing content`() {
        val directory = testDirectory()
        val missing = "$directory/missing.txt"

        assertFalse(FileReadTool().execute("""{"path":"$missing"}""").success)
        assertFalse(FileEditTool().execute("""{"path":"$missing","oldText":"a","newText":"b"}""").success)
        assertFalse(FileListTool().execute("""{"path":"$missing"}""").success)
        assertFalse(FileReadTool().execute("""{"path":"../outside.txt"}""").success)

        val existing = "$directory/existing.txt"
        FileWriteTool().execute("""{"path":"$existing","content":"unchanged"}""")
        assertFalse(FileEditTool().execute("""{"path":"$existing","oldText":"absent","newText":"new"}""").success)
    }

    private fun testDirectory(): String {
        val relative = "build/tool-tests/${java.util.UUID.randomUUID()}"
        Files.createDirectories(Path.of(relative))
        return relative
    }
}
