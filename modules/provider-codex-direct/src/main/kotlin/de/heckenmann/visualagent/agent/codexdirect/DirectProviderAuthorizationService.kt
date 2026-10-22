package de.heckenmann.visualagent.agent.codexdirect

import de.heckenmann.visualagent.agent.provider.DirectProviderAuthMode
import de.heckenmann.visualagent.agent.provider.DirectProviderAuthorization
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderAuthorizationResult
import de.heckenmann.visualagent.agent.provider.ProviderAuthorizationSession
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import io.netty.handler.codec.http.QueryStringDecoder
import org.slf4j.LoggerFactory
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.netty.http.HttpResources
import reactor.netty.http.server.HttpServer
import reactor.netty.http.server.HttpServerRequest
import reactor.netty.http.server.HttpServerResponse
import java.net.URI
import java.time.Clock
import java.time.Duration
import java.util.Optional
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Owns bounded loopback callback listeners; authorization secrets never pass through the UI. */
@Service
internal class DirectProviderAuthorizationService(
    private val installation: DirectInstallationId,
    private val store: DirectOAuthCredentialStore,
    private val protocol: DirectOAuthProtocol,
    private val sessions: DirectOAuthSessionLock,
    private val clock: Clock = Clock.systemUTC(),
) : DirectProviderAuthorization,
    SmartLifecycle {
    private val attempts = ConcurrentHashMap<String, DirectSignInTransaction>()
    private val accepting = AtomicBoolean(true)
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun begin(profile: ProviderProfile): Mono<ProviderAuthorizationSession> =
        Mono.defer {
            check(accepting.get()) { "ChatGPT sign-in is shutting down" }
            require(profile.adapter == ProviderAdapter.CODEX_DIRECT && profile.directAuthMode == DirectProviderAuthMode.CHATGPT_OAUTH) {
                "Select ChatGPT OAuth on a direct Codex profile before signing in"
            }
            require(attempts.size < 8) { "Too many pending sign-in attempts; cancel an earlier attempt" }
            val previous: Mono<Optional<DirectOAuthCredentials>> =
                profile.credentialReference?.let { reference ->
                    store.load(reference, profile.id).map { Optional.of(it) }.defaultIfEmpty(Optional.empty())
                } ?: Mono.just(Optional.empty())
            Mono.zip(installation.load(), previous).flatMap { prepared ->
                val holder = AtomicReference<DirectSignInTransaction>()
                HttpServer
                    .create()
                    .runOn(HttpResources.get(), false)
                    .host("127.0.0.1")
                    .port(0)
                    .handle { request, response ->
                        holder.get()?.let { callback(it, request, response) } ?: response.status(503).send().then()
                    }.bind()
                    .doOnDiscard(reactor.netty.DisposableServer::class.java) { it.dispose() }
                    .map { server ->
                        try {
                            val callback = URI("http://127.0.0.1:${server.port()}/auth/callback")
                            val attempt = DirectOAuthAttempt.create(profile.id, prepared.t1, callback, clock, prepared.t2.orElse(null))
                            val transaction =
                                DirectSignInTransaction(UUID.randomUUID().toString(), UUID.randomUUID().toString(), attempt, server)
                            holder.set(transaction)
                            synchronized(attempts) {
                                check(accepting.get()) { "ChatGPT sign-in is shutting down" }
                                check(attempts.size < 8) { "Too many pending sign-in attempts" }
                                attempts[transaction.id] = transaction
                            }
                            transaction.timeout.set(
                                Mono
                                    .delay(Duration.ofMinutes(5))
                                    .flatMap {
                                        transaction.timeout.set(null)
                                        expire(transaction.id)
                                    }.subscribe({}, { logger.warn("Could not invalidate expired ChatGPT sign-in credentials") }),
                            )
                            ProviderAuthorizationSession(transaction.id, requireNotNull(attempt.authorization.authorizationRequestUri))
                        } catch (error: Throwable) {
                            server.dispose()
                            throw error
                        }
                    }
            }
        }

    override fun await(sessionId: String): Mono<ProviderAuthorizationResult> =
        Mono.defer {
            val transaction = requireNotNull(attempts[sessionId]) { "The sign-in attempt expired or was cancelled" }
            Mono.usingWhen(
                Mono.just(transaction),
                { it.result.asMono() },
                {
                    Mono.fromRunnable<Void> {
                        synchronized(it) {
                            check(it.active.get() && !it.claimed.get()) { "The sign-in attempt is no longer claimable" }
                            it.claimed.set(true)
                            close(it)
                        }
                    }
                },
                { current, _ -> discard(current) },
                { discard(it) },
            )
        }

    override fun cancel(sessionId: String): Mono<Void> =
        Mono.defer {
            val transaction = attempts[sessionId] ?: return@defer Mono.empty()
            transaction.result.tryEmitError(IllegalStateException("ChatGPT sign-in cancelled"))
            discard(transaction)
        }

    /** Invalidates unclaimed credentials as well as the expired callback listener. */
    fun expire(sessionId: String): Mono<Void> =
        Mono.defer {
            val transaction = attempts[sessionId] ?: return@defer Mono.empty()
            transaction.result.tryEmitError(IllegalStateException("ChatGPT sign-in expired"))
            discard(transaction)
        }

    override fun signOut(profile: ProviderProfile): Mono<Boolean> =
        Mono.defer {
            val pending = attempts.values.filter { it.attempt.profileId == profile.id }
            Flux.fromIterable(pending).concatMap { cancel(it.id) }.then(
                profile.credentialReference?.let { reference ->
                    sessions.withSession(reference) {
                        store
                            .load(reference, profile.id)
                            .flatMap { record ->
                                protocol.revoke(record).onErrorReturn(false).flatMap { confirmed ->
                                    store.signOut(reference, profile.id).map { cleared ->
                                        check(cleared) { "The ChatGPT session changed while signing out" }
                                        confirmed
                                    }
                                }
                            }.defaultIfEmpty(true)
                    }
                } ?: Mono.just(true),
            )
        }

    private fun callback(
        transaction: DirectSignInTransaction,
        request: HttpServerRequest,
        response: HttpServerResponse,
    ): Mono<Void> =
        Mono.defer {
            if (request.method().name() != "GET" || request.uri().substringBefore('?') != "/auth/callback") {
                return@defer response.status(404).send().then()
            }
            val parameters = QueryStringDecoder(request.uri()).parameters()
            if (parameters.any { it.value.size != 1 } || parameters["state"]?.singleOrNull() != transaction.attempt.authorization.state) {
                return@defer response.status(400).send().then()
            }
            if (!transaction.active.get() || !transaction.accepted.compareAndSet(false, true)) {
                return@defer response.status(409).send().then()
            }
            protocol
                .exchange(transaction.attempt, parameters.mapValues { it.value.single() }, clock)
                .flatMap { credentials ->
                    sessions.withSession(transaction.reference) {
                        check(transaction.active.get()) { "The sign-in attempt was cancelled" }
                        store.save(transaction.reference, credentials, null).map { saved ->
                            check(saved) { "The credential registration already exists" }
                            ProviderAuthorizationResult(transaction.reference, "ChatGPT ${credentials.clientId.takeLast(8)}")
                        }
                    }
                }.flatMap { result ->
                    response
                        .sendString(Mono.just("ChatGPT authorization completed. You may return to Visual Agent."))
                        .then()
                        .doFinally { transaction.result.tryEmitValue(result) }
                }.onErrorResume {
                    response
                        .status(400)
                        .sendString(Mono.just("ChatGPT authorization failed. Return to Visual Agent and try again."))
                        .then()
                        .doFinally {
                            transaction.result.tryEmitError(IllegalStateException("ChatGPT authorization failed; retry sign-in"))
                        }
                }
        }

    private fun discard(transaction: DirectSignInTransaction): Mono<Void> =
        Mono.defer {
            val unclaimed =
                synchronized(transaction) {
                    if (transaction.claimed.get()) {
                        false
                    } else {
                        close(transaction)
                        true
                    }
                }
            if (!unclaimed) return@defer Mono.empty()
            sessions
                .withSession(transaction.reference) {
                    store.signOut(transaction.reference, transaction.attempt.profileId)
                }.then()
        }

    private fun close(transaction: DirectSignInTransaction) {
        attempts.remove(transaction.id, transaction)
        transaction.close()
    }

    override fun start() {
        accepting.set(true)
    }

    override fun isRunning(): Boolean = accepting.get()

    override fun stop() = stop {}

    override fun stop(callback: Runnable) {
        val pending =
            synchronized(attempts) {
                accepting.set(false)
                attempts.values.toList()
            }
        pending.forEach { transaction ->
            synchronized(transaction) {
                if (!transaction.claimed.get()) close(transaction)
            }
            transaction.result.tryEmitError(IllegalStateException("Visual Agent is shutting down"))
        }
        Flux
            .fromIterable(pending)
            .concatMap { transaction ->
                discard(transaction).onErrorResume {
                    logger.warn("Could not invalidate pending ChatGPT sign-in credentials during shutdown")
                    Mono.empty()
                }
            }.then()
            .subscribe({}, {
                logger.warn("Could not invalidate pending ChatGPT sign-in credentials during shutdown")
                callback.run()
            }, callback::run)
    }
}
