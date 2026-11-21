package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.tools.TerminalHost
import de.heckenmann.visualagent.agent.tools.TerminalSandboxExecution
import de.heckenmann.visualagent.agent.tools.TerminalSandboxRequest
import de.heckenmann.visualagent.knowledge.PreferenceStore
import de.heckenmann.visualagent.workspace.DirectoryGrantService
import de.heckenmann.visualagent.workspace.ServerTerminalDirectories
import de.heckenmann.visualagent.workspace.ServerTerminalDirectory
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServerTerminalConsentTest {
    @Test
    fun `legacy preferences cannot enable terminal and explicit consent survives service recreation`() {
        val values = mutableMapOf("tools.disabled.global" to "")
        val preferences = mockk<PreferenceStore>()
        every { preferences.getPreference(any()) } answers { values[firstArg<String>()] }
        every { preferences.setPreference(any(), any()) } answers {
            values[firstArg<String>()] = secondArg<String>()
        }

        val consent = ServerTerminalConsent(preferences)
        val platform = mockk<LinuxServerTerminalBackend>()
        val grants = mockk<DirectoryGrantService>()
        every { platform.supportsPlatform() } returns true
        every { grants.listGrants() } returns emptyList()
        val administration = LocalServerTerminalAdministration(consent, grants, platform)
        val config = AgentToolConfigService(mockk(relaxed = true), preferences, terminalConsent = consent)
        val terminal = ToolId("terminal")
        val execution = mockk<TerminalSandboxExecution>(relaxUnitFun = true)
        val directories = mockk<ServerTerminalDirectories>()
        every { directories.resolve(any()) } returns mockk<ServerTerminalDirectory>()
        val router = TerminalExecutionRouter(consent, directories, ServerTerminalBackend { _, _, _ -> execution })
        val request = TerminalSandboxRequest(TerminalHost.SERVER, "pwd", null, null)
        assertFalse(consent.isEnabled())
        assertNull(router.launch(request, emptyMap()))

        assertTrue(administration.snapshot().canEnable)
        assertFalse(terminal in config.mainAgentTools())
        assertTrue(administration.setEnabled(true).enabled)
        assertTrue(terminal in config.mainAgentTools())
        assertTrue(ServerTerminalConsent(preferences).isEnabled())
        assertNotNull(router.launch(request, emptyMap()))
        assertNull(router.launch(request.copy(target = TerminalHost.CLIENT), emptyMap()))

        assertFalse(administration.setEnabled(false).enabled)
        assertFalse(terminal in config.mainAgentTools())
        verify(exactly = 1) { execution.cancel() }
        assertFalse(ServerTerminalConsent(preferences).isEnabled())
        assertNull(router.launch(request, emptyMap()))
    }
}
