package de.heckenmann.visualagent.agent.codexdirect

import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardOpenOption.WRITE
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean

/** Serializes rotating-token operations within this runtime and across server processes. */
@Service
internal class DirectOAuthSessionLock(
    private val location: DirectCredentialDirectory,
) {
    private val locks = ConcurrentHashMap<String, Semaphore>()

    /** Holds one session lease across asynchronous refresh and atomic credential persistence. */
    fun <T : Any> withSession(
        reference: String,
        operation: () -> Mono<T>,
    ): Mono<T> =
        Mono.usingWhen(
            Mono
                .fromCallable { acquire(reference) }
                .subscribeOn(Schedulers.boundedElastic())
                .doOnDiscard(Lease::class.java) { it.close() },
            { operation() },
            { lease -> Mono.fromRunnable<Void> { lease.close() }.subscribeOn(Schedulers.boundedElastic()) },
        )

    private fun acquire(reference: String): Lease {
        require(UUID.fromString(reference).toString() == reference) { "Invalid credential reference" }
        val semaphore = locks.computeIfAbsent(reference) { Semaphore(1, true) }
        semaphore.acquire()
        var channel: FileChannel? = null
        try {
            val root = location.path.toAbsolutePath().normalize()
            Files.createDirectories(requireNotNull(root.parent))
            if (!Files.exists(root, NOFOLLOW_LINKS)) Files.createDirectory(root, *protectedAttributes(root.parent, true))
            requireProtectedPath(root)
            val path = root.resolve("session-$reference.lock")
            if (!Files.exists(path, NOFOLLOW_LINKS)) {
                runCatching { Files.createFile(path, *protectedAttributes(root, false)) }
                    .getOrElse { error -> if (!Files.exists(path, NOFOLLOW_LINKS)) throw error }
            }
            requireProtectedPath(path)
            channel = FileChannel.open(path, WRITE, NOFOLLOW_LINKS)
            return Lease(channel, channel.lock(), semaphore)
        } catch (error: Throwable) {
            channel?.close()
            semaphore.release()
            throw error
        }
    }

    private class Lease(
        private val channel: FileChannel,
        private val fileLock: FileLock,
        private val semaphore: Semaphore,
    ) {
        private val closed = AtomicBoolean()

        fun close() {
            if (!closed.compareAndSet(false, true)) return
            try {
                fileLock.release()
            } finally {
                try {
                    channel.close()
                } finally {
                    semaphore.release()
                }
            }
        }
    }
}
