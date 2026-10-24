package de.heckenmann.visualagent.agent.codexdirect

import org.springframework.beans.factory.DisposableBean
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean

/** Retains one current transport and drains replaced generations without closing active requests. */
@Component
internal class DirectTransportPool : DisposableBean {
    private var current: Generation? = null
    private var stopped = false

    /** Acquires a reference; an unversioned policy always receives a private generation. */
    @Synchronized
    fun acquire(
        revision: String?,
        create: () -> AutoCloseable,
    ): Lease {
        check(!stopped) { "Direct transport pool is stopped" }
        val existing = current
        val generation =
            if (revision != null && existing?.revision == revision) {
                existing
            } else {
                val next = Generation(revision, create())
                current = if (revision == null) null else next
                existing?.let(::retire)
                next.apply { retired = revision == null }
            }
        generation.references++
        return Lease(generation.resource) { release(generation) }
    }

    @Synchronized
    private fun release(generation: Generation) {
        generation.references--
        if (generation.retired && generation.references == 0) generation.resource.close()
    }

    private fun retire(generation: Generation) {
        generation.retired = true
        if (generation.references == 0) generation.resource.close()
    }

    @Synchronized
    override fun destroy() {
        stopped = true
        val previous = current
        current = null
        previous?.let(::retire)
    }

    private class Generation(
        val revision: String?,
        val resource: AutoCloseable,
    ) {
        var references = 0
        var retired = false
    }

    /** Releases only this request's reference; the pool owns physical transport shutdown. */
    class Lease(
        val resource: AutoCloseable,
        private val release: () -> Unit,
    ) : AutoCloseable {
        private val closed = AtomicBoolean()

        override fun close() {
            if (closed.compareAndSet(false, true)) release()
        }
    }
}
