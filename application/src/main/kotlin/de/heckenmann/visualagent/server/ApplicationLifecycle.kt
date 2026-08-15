package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.protocol.LifecyclePort
import org.springframework.stereotype.Component

/** Spring-owned lifecycle state used by the desktop host during application shutdown. */
@Component
class ApplicationLifecycle : LifecyclePort {
    @Volatile
    override var closing: Boolean = false

    override fun beginShutdown() {
        closing = true
    }
}
