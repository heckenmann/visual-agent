package de.heckenmann.visualagent.ui.application

import de.heckenmann.visualagent.protocol.ApplicationPort

/** Protocol-only dependencies supplied by the desktop host to the Compose shell. */
data class ComposeApplicationDependencies(
    val applicationPort: ApplicationPort,
    val beanDefinitionCount: Int = 0,
)
