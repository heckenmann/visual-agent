package de.heckenmann.visualagent.config

import de.heckenmann.visualagent.agent.codexdirect.DirectCredentialDirectory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment

/** Supplies direct-provider storage from the authoritative server data root. */
@Configuration(proxyBeanMethods = false)
class DirectProviderConfiguration {
    /** Keeps OAuth credentials server-owned and independent of working-directory or UI profile paths. */
    @Bean
    fun directCredentialDirectory(environment: Environment): DirectCredentialDirectory =
        DirectCredentialDirectory(ServerDataPathResolver.serverDataRoot(environment).resolve("credentials").resolve("codex-direct"))
}
