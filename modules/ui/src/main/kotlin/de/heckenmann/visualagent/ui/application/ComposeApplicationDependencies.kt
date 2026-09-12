package de.heckenmann.visualagent.ui.application

import de.heckenmann.visualagent.protocol.ApplicationPort
import de.heckenmann.visualagent.protocol.ClientDirectoryCapabilityRegistration
import de.heckenmann.visualagent.protocol.ClientDirectoryGrantAdministrationPort
import de.heckenmann.visualagent.protocol.ClientImagePort
import de.heckenmann.visualagent.protocol.ConversationImageResolution
import de.heckenmann.visualagent.protocol.DirectoryAccessMode

/** Protocol-only dependencies supplied by the desktop host to the Compose shell. */
data class ComposeApplicationDependencies(
    val applicationPort: ApplicationPort,
    val beanDefinitionCount: Int = 0,
    val clientImagePort: ClientImagePort = UnavailableClientImagePort,
    val clientDirectoryAccess: ClientDirectoryGrantAdministrationPort = UnavailableClientDirectoryGrantAdministrationPort,
)

private object UnavailableClientImagePort : ClientImagePort {
    override suspend fun resolveImage(source: String): ConversationImageResolution =
        ConversationImageResolution.Rejected("Client image loading is not available")
}

private object UnavailableClientDirectoryGrantAdministrationPort : ClientDirectoryGrantAdministrationPort {
    override fun prepareDirectoryGrant(
        absolutePath: String,
        mode: DirectoryAccessMode,
    ): ClientDirectoryCapabilityRegistration = error("Client directory access is not available")

    override fun reactivateDirectoryGrant(
        grantId: String,
        absolutePath: String,
        mode: DirectoryAccessMode,
    ): ClientDirectoryCapabilityRegistration = error("Client directory access is not available")

    override fun revokeDirectoryGrant(grantId: String) = Unit
}
