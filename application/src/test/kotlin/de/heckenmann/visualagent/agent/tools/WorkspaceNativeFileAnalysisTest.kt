package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.AgentConfig
import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.ChatResponse
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.workspace.WorkspaceFileService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.ai.content.Media
import org.springframework.beans.factory.ObjectProvider
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.nio.file.Path
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkspaceNativeFileAnalysisTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `native file tool sends managed bytes only after an explicit subscribed action`() {
        val files = WorkspaceFileService(FakeWorkspaceFileStore(), directory.resolve("data/db").toString())
        val record = files.createManagedFile("imports", "notes.txt", "private notes".toByteArray(), "text/plain")
        val provider = mockk<LLMProvider>()
        val source = mockk<ObjectProvider<LLMProvider>>()
        every { source.getObject() } returns provider
        val media = slot<Media>()
        val request = slot<ChatRequestContext>()
        every { provider.fileReactive(capture(media), capture(request)) } returns
            Mono.just(ChatResponse("selected", Message("assistant", "summary"), true))
        val tool = WorkspaceFileTool(WorkspaceFileToolPortAdapter(files, source))
        val result = tool.executeReactive("""{"action":"analyzeFile","id":"${record.id}","prompt":"summarize"}""", emptyMap())

        verify(exactly = 0) { provider.fileReactive(any(), any()) }
        StepVerifier
            .create(result)
            .assertNext {
                assertTrue(it.success)
                assertTrue(it.content.contains("summary"))
                assertFalse(it.content.contains("private notes"))
            }.verifyComplete()
        assertContentEquals("private notes".toByteArray(), media.captured.data as ByteArray)
        assertEquals(
            "summarize",
            request.captured.messages
                .single()
                .content,
        )
        assertEquals("notes.txt", media.captured.name)
        assertTrue(request.captured.enabledTools.isEmpty())
    }

    @Test
    fun `path escapes cannot trigger native file inference`() {
        val files = WorkspaceFileService(FakeWorkspaceFileStore(), directory.resolve("data/db").toString())
        val source = mockk<ObjectProvider<LLMProvider>>()
        val tool = WorkspaceFileTool(WorkspaceFileToolPortAdapter(files, source))

        StepVerifier
            .create(tool.executeReactive("""{"action":"analyzeFile","path":"../secret.txt","prompt":"read"}""", emptyMap()))
            .assertNext { assertFalse(it.success) }
            .verifyComplete()
        verify(exactly = 0) { source.getObject() }
    }

    @Test
    fun `native file analysis uses the authenticated subagent selection instead of the main conversation`() {
        val files = WorkspaceFileService(FakeWorkspaceFileStore(), directory.resolve("data/db").toString())
        val record = files.createManagedFile("imports", "notes.txt", "notes".toByteArray(), "text/plain")
        val source = mockk<ObjectProvider<LLMProvider>>()
        val provider = mockk<LLMProvider>()
        every { source.getObject() } returns provider
        val request = slot<ChatRequestContext>()
        every { provider.fileReactive(any(), capture(request)) } returns
            Mono.just(ChatResponse("assigned", Message("assistant", "summary"), true))
        val agents = mockk<AgentManager>()
        every { agents.getSubAgent("worker") } returns
            SubAgent("worker", "Worker", "analyst", config = AgentConfig(provider = "secondary", model = "assigned"))
        val tool = WorkspaceFileTool(WorkspaceFileToolPortAdapter(files, source, agents = agents))

        StepVerifier
            .create(
                tool.executeReactive(
                    """{"action":"analyzeFile","id":"${record.id}","prompt":"read"}""",
                    mapOf(
                        "agentId" to "worker",
                    ),
                ),
            ).assertNext { assertTrue(it.success) }
            .verifyComplete()

        assertEquals("secondary", request.captured.provider)
        assertEquals("assigned", request.captured.model)
    }

    @Test
    fun `files beyond the inline payload limit fail without obtaining a provider`() {
        val files = WorkspaceFileService(FakeWorkspaceFileStore(), directory.resolve("data/db").toString())
        val record = files.createManagedFile("imports", "large.txt", ByteArray(8 * 1024 * 1024 + 1), "text/plain")
        val source = mockk<ObjectProvider<LLMProvider>>()
        val tool = WorkspaceFileTool(WorkspaceFileToolPortAdapter(files, source))

        StepVerifier
            .create(tool.executeReactive("""{"action":"analyzeFile","id":"${record.id}","prompt":"read"}""", emptyMap()))
            .assertNext { assertFalse(it.success) }
            .verifyComplete()
        verify(exactly = 0) { source.getObject() }
    }
}
