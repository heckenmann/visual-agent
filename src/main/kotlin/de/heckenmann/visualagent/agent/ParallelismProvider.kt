package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.config.AppConfigBean
import org.springframework.stereotype.Component

/**
 * Provides the current maximum number of concurrently running sub-agent jobs.
 *
 * Wraps a config read so the value can change at runtime without restart.
 *
 * The no-argument constructor is intended for tests; production code should inject
 * a real [AppConfigBean].
 */
@Component
open class ParallelismProvider(
    private val appConfig: AppConfigBean = AppConfigBean(),
) {
    /**
     * Returns the current parallelism limit.
     */
    open fun get(): Int = appConfig.maxParallelSubAgents.coerceAtLeast(1)
}
