package de.heckenmann.visualagent.agent.provider

import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.NoTransactionException
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import reactor.core.scheduler.Schedulers

/** Publishes catalog refresh hints only after the enclosing persistence transaction commits. */
@Service
class ProviderCatalogNotifications(
    private val persistence: ProviderCatalogPersistence,
    private val runtime: ProviderRuntimeConfig,
) {
    private val logger = KotlinLogging.logger {}
    private val sink = Sinks.many().multicast().directBestEffort<Unit>()
    private val emissionLock = Any()

    /** Hot refresh hints; subscribers reload the authoritative persisted catalog. */
    val changes: Flux<Unit> = sink.asFlux()

    internal fun publish() {
        val publication = {
            synchronized(emissionLock) {
                // A delayed publication must not restore a selection from an older committed write.
                runtime.llmProvider = persistence.load().activeProviderId
                val result = sink.tryEmitNext(Unit)
                if (result != Sinks.EmitResult.OK) logger.warn { "Unable to emit provider catalog change: $result" }
            }
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCommit() {
                        publication()
                    }
                },
            )
        } else {
            publication()
        }
    }

    internal fun publishReactive(): Mono<Void> =
        Mono.defer {
            val publication = Mono.fromRunnable<Void> { publish() }.subscribeOn(Schedulers.boundedElastic())
            org.springframework.transaction.reactive.TransactionSynchronizationManager
                .forCurrentTransaction()
                .flatMap { manager ->
                    if (manager.isSynchronizationActive) {
                        manager.registerSynchronization(
                            object : org.springframework.transaction.reactive.TransactionSynchronization {
                                override fun afterCommit(): Mono<Void> = publication
                            },
                        )
                        Mono.empty<Void>()
                    } else {
                        publication
                    }
                }.onErrorResume(NoTransactionException::class.java) { publication }
        }
}
