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

    /** Registers a listener that is notified when the configured limit changes. */
    open fun addChangeListener(listener: () -> Unit): AutoCloseable =
        appConfig.addChangeListener { change ->
            if (change.key == AppConfigBean.KEY_SESSION_MAX_PARALLEL_SUB_AGENTS) listener()
        }
}
