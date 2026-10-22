package de.heckenmann.visualagent.agent.codexdirect

import de.heckenmann.visualagent.agent.provider.ProviderAuthorizationResult
import reactor.core.Disposable
import reactor.core.publisher.Sinks
import reactor.netty.DisposableServer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Request-scoped lifecycle; never persisted or attached to ordinary conversation messages. */
internal data class DirectSignInTransaction(
    val id: String,
    val reference: String,
    val attempt: DirectOAuthAttempt,
    val server: DisposableServer,
    val result: Sinks.One<ProviderAuthorizationResult> = Sinks.one(),
    val accepted: AtomicBoolean = AtomicBoolean(),
    val active: AtomicBoolean = AtomicBoolean(true),
    val claimed: AtomicBoolean = AtomicBoolean(),
    val timeout: AtomicReference<Disposable> = AtomicReference(),
) {
    /** Disposes listener and deadline exactly once, independently from callback outcome. */
    fun close() {
        if (active.compareAndSet(true, false)) {
            timeout.getAndSet(null)?.dispose()
            server.dispose()
        }
    }

    override fun toString(): String = "DirectSignInTransaction(redacted)"
}
