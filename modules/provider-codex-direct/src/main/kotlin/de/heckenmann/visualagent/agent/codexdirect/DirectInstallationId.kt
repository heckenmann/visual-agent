package de.heckenmann.visualagent.agent.codexdirect

import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.nio.channels.FileChannel
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import java.util.UUID

/** Persists the OpenAI host identifier separately from account and client registrations. */
@Service
internal class DirectInstallationId(
    private val directory: DirectCredentialDirectory,
) {
    private val lock = Any()

    /** Resolves the same host ID across application restarts without touching the UI thread. */
    fun load(): Mono<String> =
        Mono
            .fromCallable { synchronized(lock) { loadLocked() } }
            .subscribeOn(Schedulers.boundedElastic())

    private fun loadLocked(): String {
        val root = directory.path.toAbsolutePath().normalize()
        Files.createDirectories(requireNotNull(root.parent))
        if (!Files.exists(root, NOFOLLOW_LINKS)) {
            try {
                Files.createDirectory(root, *protectedAttributes(root.parent, true))
            } catch (_: FileAlreadyExistsException) {
            }
        }
        requireProtectedPath(root)
        val lockPath = root.resolve("host-id.lock")
        FileChannel.open(lockPath, setOf(CREATE, WRITE, NOFOLLOW_LINKS), *protectedAttributes(root, false)).use { channel ->
            requireProtectedPath(lockPath)
            channel.lock().use {
                val path = root.resolve("host-id")
                if (!Files.exists(path, NOFOLLOW_LINKS)) {
                    try {
                        Files.createFile(path, *protectedAttributes(root, false))
                        Files.writeString(path, "urn:uuid:${UUID.randomUUID()}", WRITE)
                    } catch (_: FileAlreadyExistsException) {
                    }
                }
                requireProtectedPath(path)
                require(Files.size(path) in 1..64) { "Invalid installation host ID" }
                val value = Files.readString(path).trim()
                require(value == "urn:uuid:${UUID.fromString(value.removePrefix("urn:uuid:"))}") { "Invalid installation host ID" }
                return value
            }
        }
    }
}
