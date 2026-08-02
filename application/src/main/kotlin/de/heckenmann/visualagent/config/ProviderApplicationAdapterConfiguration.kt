package de.heckenmann.visualagent.config

import de.heckenmann.visualagent.agent.provider.ProviderWorkingDirectory
import de.heckenmann.visualagent.workspace.WorkspaceFilePaths
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Supplies application-owned adapters required by provider beans. */
@Configuration
class ProviderApplicationAdapterConfiguration {
    /** Supplies the managed workspace as the default provider process directory. */
    @Bean
    fun providerWorkingDirectory(appConfig: AppConfigBean): ProviderWorkingDirectory =
        ProviderWorkingDirectory { WorkspaceFilePaths.workspaceRoot(appConfig.databasePath) }
}
