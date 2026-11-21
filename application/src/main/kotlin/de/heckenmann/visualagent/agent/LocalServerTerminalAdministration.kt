package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.protocol.DirectoryAccessMode
import de.heckenmann.visualagent.protocol.DirectoryGrantOrigin
import de.heckenmann.visualagent.protocol.TerminalConsentPort
import de.heckenmann.visualagent.protocol.TerminalConsentSnapshot
import de.heckenmann.visualagent.workspace.DirectoryGrantService
import org.springframework.stereotype.Component

/** Administration reached directly by the embedded desktop, outside model-facing tool settings. */
@Component
internal class LocalServerTerminalAdministration(
    private val consent: ServerTerminalConsent,
    private val grants: DirectoryGrantService,
    private val backend: LinuxServerTerminalBackend,
) : TerminalConsentPort {
    override fun snapshot(): TerminalConsentSnapshot {
        val ready = backend.supportsPlatform()
        return TerminalConsentSnapshot(
            enabled = consent.isEnabled(),
            canEnable = ready,
            executionNotice =
                "Commands on the application server run with the server process's operating-system account permissions, " +
                    "further restricted by the terminal sandbox." +
                    if (ready) "" else " A working Bubblewrap sandbox on Linux x86-64 is required.",
            directories =
                listOf("Managed server workspace (read and write)") +
                    grants.listGrants().filter { it.origin == DirectoryGrantOrigin.SERVER && grants.isAvailable(it) }.map { grant ->
                        val access = if (grant.mode == DirectoryAccessMode.READ_WRITE) "read and write" else "read only"
                        "${grant.displayName} ($access)"
                    },
        )
    }

    override fun setEnabled(enabled: Boolean): TerminalConsentSnapshot {
        check(!enabled || backend.supportsPlatform()) { "The server terminal sandbox is unavailable." }
        consent.setEnabled(enabled)
        return snapshot()
    }
}
