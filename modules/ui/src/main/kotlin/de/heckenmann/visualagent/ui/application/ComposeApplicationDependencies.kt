package de.heckenmann.visualagent.ui.application

import de.heckenmann.visualagent.protocol.ApplicationPort
import de.heckenmann.visualagent.protocol.ClientImagePort
import de.heckenmann.visualagent.protocol.ConversationImageResolution

/** Protocol-only dependencies supplied by the desktop host to the Compose shell. */
data class ComposeApplicationDependencies(
    val applicationPort: ApplicationPort,
    val beanDefinitionCount: Int = 0,
    val clientImagePort: ClientImagePort = UnavailableClientImagePort,
)

private object UnavailableClientImagePort : ClientImagePort {
    override suspend fun resolveImage(source: String): ConversationImageResolution =
        ConversationImageResolution.Rejected("Client image loading is not available")
}
