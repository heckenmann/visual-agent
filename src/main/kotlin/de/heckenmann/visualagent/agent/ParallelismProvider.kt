package de.heckenmann.visualagent.agent

import org.springframework.stereotype.Component

/**
 * Provides the current maximum number of concurrently running sub-agent jobs.
 *
 * Wraps a config read so the value can change at runtime without restart.
 */
@Component
open class ParallelismProvider {
    /**
     * Returns the current parallelism limit.
     */
    open fun get(): Int = 4 // TODO: read from AppConfigBean when available
}
