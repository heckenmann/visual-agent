package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.ChatResponse
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.ShowResponse
import de.heckenmann.visualagent.knowledge.WorkspaceFileRecord
import de.heckenmann.visualagent.knowledge.WorkspaceFileStore
import de.heckenmann.visualagent.testsupport.TestPng
import de.heckenmann.visualagent.workspace.WorkspaceFileService
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.junit.jupiter.api.Test
import org.springframework.beans.BeansException
import org.springframework.beans.factory.ObjectProvider
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Stream
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkspaceFileToolTest {
    @Test
    fun `workspace file tool lists reads hashes and analyzes images`() {
        val dbPath = tempDir().resolve("data/visual-agent.db").toString()
        val service = WorkspaceFileService(FakeWorkspaceFileStore(), dbPath)
        val textFile = tempDir().resolve("notes.txt")
        Files.writeString(textFile, "Alpha")
        val imageFile = tempDir().resolve("pixel.png")
        TestPng.write(imageFile, 1, 1)
        val text = service.importFile(textFile.toFile())
        val image = service.importFile(imageFile.toFile())
        val tool = WorkspaceFileTool(service, SingleObjectProvider(FakeVisionProvider()))

        assertTrue(tool.definition.description.contains("![alt text](workspace:<path>)"))
        assertTrue(tool.definition.description.contains("Do not invent paths"))
        assertTrue(tool.definition.description.contains("native filesystem sandbox is read-only"))
        assertTrue(tool.definition.description.contains("do not run native or terminal permission preflight checks"))
        assertTrue(tool.definition.description.contains("its result is authoritative"))

        val list = tool.execute("""{"action":"list"}""")
        val read = tool.execute("""{"action":"readText","id":"${text.id}"}""")
        val hash = Json.parseToJsonElement(tool.execute("""{"action":"hash","path":"${text.relativePath}"}""").content).jsonObject
        val info = Json.parseToJsonElement(tool.execute("""{"action":"imageInfo","id":"${image.id}"}""").content).jsonObject
        val analysis = tool.execute("""{"action":"analyzeImage","id":"${image.id}","prompt":"describe"}""")

        assertTrue(list.content.contains(text.relativePath))
        assertTrue(list.content.contains("imports"))
        assertTrue(read.content.contains("Alpha"))
        assertEquals("sha256", hash["algorithm"]!!.jsonPrimitive.content)
        assertEquals("1", info["width"]!!.jsonPrimitive.content)
        assertTrue(analysis.content.contains("vision ok"))
    }

    @Test
    fun `workspace file tool extracts pdf renders page preview and handles invalid actions`() {
        val dbPath = tempDir().resolve("data/visual-agent.db").toString()
        val service = WorkspaceFileService(FakeWorkspaceFileStore(), dbPath)
        val pdfFile = tempDir().resolve("sample.pdf")
        writePdf(pdfFile, "Tool PDF")
        val pdf = service.importFile(pdfFile.toFile())
        val tool = WorkspaceFileTool(service, SingleObjectProvider(FakeVisionProvider()))

        val info = tool.execute("""{"action":"info","id":"${pdf.id}"}""")
        val text = tool.execute("""{"action":"extractPdfText","path":"${pdf.relativePath}"}""")
        val page = Json.parseToJsonElement(tool.execute("""{"action":"renderPdfPage","id":"${pdf.id}","page":1}""").content).jsonObject
        val unsupported = tool.execute("""{"action":"missing"}""")

        assertTrue(info.content.contains("application/pdf"))
        assertTrue(text.content.contains("Tool PDF"))
        assertEquals("generated/sample-page-1.png", page["path"]!!.jsonPrimitive.content)
        assertEquals("image/png", page["mimeType"]!!.jsonPrimitive.content)
        assertFalse(unsupported.success)
    }

    @Test
    fun `workspace file tool searches and syncs workspace files`() {
        val dbPath = tempDir().resolve("data/visual-agent.db").toString()
        val service = WorkspaceFileService(FakeWorkspaceFileStore(), dbPath)
        val imported = service.createManagedFile("imports", "notes.txt", "Needle content".toByteArray(), "text/plain")
        val unmanaged = service.workspaceRoot().resolve("imports/manual.txt")
        unmanaged.parent.toFile().mkdirs()
        unmanaged.writeText("manual")
        val tool = WorkspaceFileTool(service, SingleObjectProvider(FakeVisionProvider()))

        service.createDirectory("", "needle-directory")
        val search = tool.execute("""{"action":"search","query":"needle"}""")
        val directorySearch = tool.execute("""{"action":"search","query":"needle","entryType":"directory"}""")
        val mimeSearch = tool.execute("""{"action":"search","query":"needle","entryType":"file","mimeType":"text/plain"}""")
        val nonMatchingMimeSearch =
            tool.execute("""{"action":"search","query":"needle","entryType":"file","mimeType":"image/png"}""")
        val sync = Json.parseToJsonElement(tool.execute("""{"action":"sync"}""").content).jsonObject

        assertTrue(search.content.contains(imported.relativePath))
        assertTrue(search.content.contains("needle-directory"))
        assertTrue(directorySearch.content.contains("needle-directory"))
        assertFalse(directorySearch.content.contains(imported.relativePath))
        assertTrue(mimeSearch.content.contains(imported.relativePath))
        assertFalse(nonMatchingMimeSearch.content.contains(imported.relativePath))
        assertEquals("1", sync["added"]!!.jsonPrimitive.content)
    }

    @Test
    fun `workspace MIME search limits results after filtering`() {
        val service = WorkspaceFileService(FakeWorkspaceFileStore(), tempDir().resolve("data/visual-agent.db").toString())
        repeat(50) { index ->
            service.createManagedFile("imports", "match-$index.bin", byteArrayOf(1), "application/octet-stream")
        }
        val text = service.createManagedFile("imports", "match-text.txt", "content".toByteArray(), "text/plain")
        val tool = WorkspaceFileTool(service, SingleObjectProvider(FakeVisionProvider()))

        val result = tool.execute("""{"action":"search","query":"match","mimeType":"text/plain"}""")

        assertTrue(result.content.contains(text.relativePath))
    }

    @Test
    fun `workspace file tool creates an empty directory through the server port`() {
        val port = mockk<de.heckenmann.visualagent.agent.tools.api.WorkspaceFileToolPort>()
        every { port.createDirectory("projects", "demo") } returns "projects/demo"
        val tool = WorkspaceFileTool(port)

        val result = tool.execute("""{"action":"createDirectory","parentDirectory":"projects","name":"demo"}""")

        assertTrue(result.success)
        assertTrue(result.content.contains("projects/demo"))
    }

    @Test
    fun `workspace file delete uses the server tool and removes metadata`() {
        val dbPath = tempDir().resolve("data/visual-agent.db").toString()
        val service = WorkspaceFileService(FakeWorkspaceFileStore(), dbPath)
        val source = tempDir().resolve("remove-me.txt")
        Files.writeString(source, "remove")
        val imported = service.importFile(source.toFile())
        val tool = WorkspaceFileTool(service, SingleObjectProvider(FakeVisionProvider()))

        val result = Json.parseToJsonElement(tool.execute("""{"action":"delete","id":"${imported.id}"}""").content).jsonObject

        assertEquals("true", result["deleted"]!!.jsonPrimitive.content)
        assertFalse(Files.exists(service.workspaceRoot().resolve(imported.relativePath)))
        assertTrue(service.listFiles().none { it.id == imported.id })
    }

    @Test
    fun `workspace file tool deletes directories only when recursion is explicit`() {
        val dbPath = tempDir().resolve("data/visual-agent.db").toString()
        val service = WorkspaceFileService(FakeWorkspaceFileStore(), dbPath)
        val imported = service.importFile("remove-me/nested", "file.txt", "remove".toByteArray())
        val tool = WorkspaceFileTool(service, SingleObjectProvider(FakeVisionProvider()))

        val result =
            Json
                .parseToJsonElement(tool.execute("""{"action":"deleteDirectory","path":"remove-me","recursive":true}""").content)
                .jsonObject

        assertEquals("true", result["recursive"]!!.jsonPrimitive.content)
        assertEquals("1", result["deletedFiles"]!!.jsonPrimitive.content)
        assertTrue(service.listFiles().none { it.id == imported.id })
    }

    private fun tempDir(): Path = Files.createTempDirectory("visual-agent-workspace-tool-test")

    private fun writePdf(
        path: Path,
        text: String,
    ) {
        PDDocument().use { document ->
            val page = PDPage()
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f)
                stream.newLineAtOffset(72f, 720f)
                stream.showText(text)
                stream.endText()
            }
            document.save(path.toFile())
        }
    }

    private class FakeVisionProvider : LLMProvider {
        override suspend fun chat(messages: List<Message>): ChatResponse =
            ChatResponse(model = "fake", message = Message("assistant", "chat"), done = true)

        override suspend fun stream(messages: List<Message>): Flow<ChatResponse> = emptyFlow()

        override suspend fun vision(
            image: ByteArray,
            prompt: String,
        ): ChatResponse = ChatResponse(model = "vision-model", message = Message("assistant", "vision ok: $prompt"), done = true)

        override suspend fun embeddings(text: String): List<Double> = emptyList()

        override fun isConnected(): Boolean = true

        override suspend fun checkConnection(): Boolean = true

        override suspend fun getModels(): List<String> = listOf("vision-model")

        override suspend fun getModelDetails(modelName: String): ShowResponse = ShowResponse(modelName, "")
    }

    private class SingleObjectProvider<T : Any>(
        private val value: T,
    ) : ObjectProvider<T> {
        override fun getObject(): T = value

        override fun getObject(vararg args: Any?): T = value

        override fun getIfAvailable(): T = value

        override fun getIfUnique(): T = value

        override fun iterator(): MutableIterator<T> = mutableListOf(value).iterator()

        @Throws(BeansException::class)
        override fun stream(): Stream<T> = Stream.of(value)

        @Throws(BeansException::class)
        override fun orderedStream(): Stream<T> = stream()
    }

    private class FakeWorkspaceFileStore : WorkspaceFileStore {
        private val records = linkedMapOf<String, WorkspaceFileRecord>()

        override fun saveWorkspaceFile(record: WorkspaceFileRecord) {
            records[record.id] = record
        }

        override fun listWorkspaceFiles(): List<WorkspaceFileRecord> = records.values.toList()

        override fun getWorkspaceFile(id: String): WorkspaceFileRecord? = records[id]

        override fun getWorkspaceFileByPath(relativePath: String): WorkspaceFileRecord? =
            records.values.firstOrNull { it.relativePath == relativePath }

        override fun deleteWorkspaceFile(id: String): Boolean = records.remove(id) != null
    }
}
