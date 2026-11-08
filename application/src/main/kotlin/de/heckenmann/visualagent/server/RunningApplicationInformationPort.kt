package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.protocol.ApplicationInformation
import de.heckenmann.visualagent.protocol.ApplicationInformationPort
import org.springframework.stereotype.Component

/** Supplies non-sensitive metadata from the local packaged runtime. */
@Component
internal class RunningApplicationInformationPort(
    private val versionSource: de.heckenmann.visualagent.update.ApplicationVersionSource,
) : ApplicationInformationPort {
    override fun current(): ApplicationInformation =
        ApplicationInformation(
            version = versionSource.currentVersion(),
            runtimeName = System.getProperty("java.vm.name", "Java runtime"),
            runtimeVersion = Runtime.version().toString(),
            operatingSystem = System.getProperty("os.name", "Unknown OS"),
            operatingSystemVersion = System.getProperty("os.version", "Unknown version"),
            architecture = System.getProperty("os.arch", "Unknown architecture"),
        )
}
