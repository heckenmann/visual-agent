package de.heckenmann.visualagent.config

import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Provides a single application-scoped [CoroutineScope] for background jobs.
 *
 * The scope uses [SupervisorJob] so a failure in one child does not cancel siblings,
 * and [Dispatchers.Default] for CPU-bound work. Beans that need a scope receive it
 * via constructor injection instead of creating their own.
 */
@Configuration
class ApplicationCoroutineScope {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Application-wide coroutine scope.
     *
     * @return Shared scope that lives for the application lifetime
     */
    @Bean
    fun applicationScope(): CoroutineScope = scope

    /**
     * Cancels the scope when the Spring context shuts down.
     */
    @PreDestroy
    fun destroy() {
        scope.cancel()
    }
}
