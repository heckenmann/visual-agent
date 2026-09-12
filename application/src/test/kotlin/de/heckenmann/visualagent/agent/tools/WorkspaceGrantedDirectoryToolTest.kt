package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.DirectoryToolPort
import de.heckenmann.visualagent.agent.tools.api.ToolDirectoryEntry
import de.heckenmann.visualagent.agent.tools.api.ToolDirectoryGrant
import de.heckenmann.visualagent.agent.tools.api.ToolDirectoryMatch
import de.heckenmann.visualagent.agent.tools.api.ToolDirectoryMimeType
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class WorkspaceGrantedDirectoryToolTest {
    @Test
    fun `workspace file tool exposes all granted-directory operations`() {
        val directories = mockk<DirectoryToolPort>()
        every { directories.listGrants() } returns
            listOf(ToolDirectoryGrant("grant", "Documents", "CLIENT", "READ_WRITE", true))
        every { directories.list("grant", "") } returns listOf(ToolDirectoryEntry("notes.txt", false, 14))
        every { directories.detectMimeType("grant", "notes.txt") } returns ToolDirectoryMimeType("text/plain", 14)
        every { directories.search("grant", "needle", "") } returns listOf(ToolDirectoryMatch("notes.txt", 1, "needle"))
        every { directories.glob("grant", "", "*.txt") } returns listOf(ToolDirectoryEntry("notes.txt", false, 14))
        every { directories.writeText("grant", "notes.txt", "updated") } returns "notes.txt"
        every { directories.editText("grant", "notes.txt", "before", "after") } returns "notes.txt"
        every { directories.createDirectory("grant", "new-folder") } returns "new-folder"
        every { directories.delete("grant", any(), any()) } returns Unit

        val tool = WorkspaceFileTool(mockk(relaxed = true), directories)
        val results =
            listOf(
                tool.execute("""{"action":"listRoots"}"""),
                tool.execute("""{"action":"list","rootId":"grant","path":""}"""),
                tool.execute("""{"action":"mime","rootId":"grant","path":"notes.txt"}"""),
                tool.execute("""{"action":"search","rootId":"grant","query":"needle"}"""),
                tool.execute("""{"action":"glob","rootId":"grant","path":"","pattern":"*.txt"}"""),
                tool.execute("""{"action":"grep","rootId":"grant","path":"","query":"needle"}"""),
                tool.execute("""{"action":"writeText","rootId":"grant","path":"notes.txt","content":"updated"}"""),
                tool.execute("""{"action":"edit","rootId":"grant","path":"notes.txt","oldText":"before","newText":"after"}"""),
                tool.execute("""{"action":"createDirectory","rootId":"grant","path":"new-folder"}"""),
                tool.execute("""{"action":"delete","rootId":"grant","path":"notes.txt"}"""),
                tool.execute("""{"action":"deleteDirectory","rootId":"grant","path":"old-folder","recursive":true}"""),
            )

        assertTrue(results.all { it.success })
    }
}
